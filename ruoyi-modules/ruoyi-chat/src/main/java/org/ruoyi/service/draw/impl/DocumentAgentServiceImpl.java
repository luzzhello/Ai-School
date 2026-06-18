package org.ruoyi.service.draw.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.ErDiagramProperties;
import org.ruoyi.domain.dto.request.DocumentAgentStartRequest;
import org.ruoyi.domain.entity.draw.DocAgentSession;
import org.ruoyi.domain.entity.draw.DocAgentTaskDef;
import org.ruoyi.domain.entity.draw.DocAgentTaskRecord;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.domain.vo.draw.DocumentAgentSessionDetailVo;
import org.ruoyi.domain.vo.draw.DocumentAgentSessionVo;
import org.ruoyi.domain.vo.draw.DocumentAgentStartVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.DocumentAgentSessionStatus;
import org.ruoyi.service.draw.DocumentAgentTaskStatus;
import org.ruoyi.service.draw.IDocumentAgentService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAgentServiceImpl implements IDocumentAgentService {

  private static final String PROMPT_CODE = "smart_doc_agent";
  private static final int TASK_MAX_RETRIES = 3;

  private static final List<String> AGENT_MESSAGES = List.of(
    "让我梳理一下任务进度...",
    "正在分析系统功能点...",
    "正在组织章节结构...",
    "正在检索相关技术资料...",
    "正在完善文档细节..."
  );

  private final IChatModelService chatModelService;
  private final IChatPromptService chatPromptService;
  private final ErDiagramProperties erDiagramProperties;
  private final IFeatureCoinService featureCoinService;
  private final DocumentAgentWordPackager packager;
  private final DocumentAgentPersistence persistence;
  private final ObjectMapper objectMapper;

  private final Map<String, RuntimeState> runtimeMap = new ConcurrentHashMap<>();
  private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "document-agent");
    t.setDaemon(true);
    return t;
  });

  @Override
  public DocumentAgentStartVo start(DocumentAgentStartRequest request, Long userId) {
    if (userId == null) {
      throw new ServiceException("请先登录");
    }
    featureCoinService.requireAffordable(userId, FeatureCodes.SMART_DOC_AI, null);
    long costCoins = featureCoinService.charge(userId, FeatureCodes.SMART_DOC_AI, null);

    String sessionId = UUID.randomUUID().toString().replace("-", "");
    persistence.createSession(sessionId, userId, request.getDescription().trim(), request.getModel(), costCoins);
    return new DocumentAgentStartVo(sessionId);
  }

  @Override
  public void stream(String sessionId, Long userId, SseEmitter emitter) {
    DocAgentSession session = persistence.requireSession(sessionId, userId);
    if (DocumentAgentSessionStatus.COMPLETED.equals(session.getStatus())) {
      executor.submit(() -> replaySession(session, emitter));
      return;
    }
    RuntimeState runtime = runtimeMap.computeIfAbsent(sessionId, k -> new RuntimeState());
    if (runtime.running.get()) {
      throw new ServiceException("任务正在执行中，请稍候");
    }
    runtime.cancelled.set(false);
    runtime.running.set(true);
    runtime.future = executor.submit(() -> runPipeline(session, emitter, runtime));
  }

  @Override
  public void stop(String sessionId, Long userId) {
    DocAgentSession session = persistence.requireSession(sessionId, userId);
    if (DocumentAgentSessionStatus.COMPLETED.equals(session.getStatus())) {
      return;
    }
    RuntimeState runtime = runtimeMap.get(sessionId);
    String currentTaskCode = session.getFailedTaskCode();
    if (runtime != null) {
      runtime.cancelled.set(true);
      if (StringUtils.isNotBlank(runtime.currentTaskCode)) {
        currentTaskCode = runtime.currentTaskCode;
      }
      if (runtime.future != null) {
        runtime.future.cancel(true);
      }
    }
    persistence.markSessionStopped(sessionId, currentTaskCode);
  }

  @Override
  public List<DocumentAgentSessionVo> listSessions(Long userId, Integer limit) {
    return persistence.listUserSessions(userId, limit == null ? 50 : limit);
  }

  @Override
  public DocumentAgentSessionDetailVo getSessionDetail(String sessionId, Long userId) {
    return persistence.getSessionDetail(sessionId, userId, true);
  }

  private void replaySession(DocAgentSession session, SseEmitter emitter) {
    try {
      List<DocAgentTaskRecord> records = persistence.listTaskRecords(session.getSessionId());
      for (DocAgentTaskRecord record : records) {
        if (StringUtils.isNotBlank(record.getContent())) {
          replayTaskProgress(emitter, record, false);
        }
      }
      if (StringUtils.isNotBlank(session.getDownloadBase64())) {
        sendEvent(emitter, event("complete", "packaging", "整体打包", null, "所有任务已完成", null, Map.of(
          "projectTitle", session.getProjectTitle(),
          "downloadFileName", session.getDownloadFileName(),
          "downloadBase64", session.getDownloadBase64()
        )));
      }
      emitter.complete();
    } catch (Exception e) {
      log.error("回放智能文档 Agent 失败, sessionId={}", session.getSessionId(), e);
      sendError(emitter, "回放任务失败", null, true);
    }
  }

  private void runPipeline(DocAgentSession session, SseEmitter emitter, RuntimeState runtime) {
    String sessionId = session.getSessionId();
    int msgIndex = 0;
    String currentTaskCode = null;
    try {
      List<DocAgentTaskDef> defs = persistence.listEnabledTaskDefs();
      Map<String, String> artifacts = persistence.loadArtifactMap(sessionId);
      ChatModel model = buildModel(resolveModelName(session.getModelName()));
      String systemPrompt = loadSystemPrompt();

      for (DocAgentTaskDef def : defs) {
        if (runtime.cancelled.get() || Thread.currentThread().isInterrupted()) {
          persistence.markSessionStopped(sessionId, currentTaskCode);
          sendError(emitter, "任务已停止，可点击继续生成", currentTaskCode, true);
          return;
        }

        currentTaskCode = def.getTaskCode();
        runtime.currentTaskCode = def.getTaskCode();
        String cached = artifacts.get(def.getTaskCode());
        if (StringUtils.isNotBlank(cached)) {
          replayTaskProgress(emitter, toRecord(def, cached), false);
          continue;
        }

        try {
          persistence.markTaskRunning(sessionId, def.getTaskCode());
          sendEvent(emitter, event("task_start", def.getTaskCode(), def.getTaskTitle(), null, null, null, null));
          sendEvent(emitter, event("tool", def.getTaskCode(), def.getTaskTitle(), def.getToolName(), null, null, null));

          String statusMsg = AGENT_MESSAGES.get(msgIndex % AGENT_MESSAGES.size());
          msgIndex++;
          sendEvent(emitter, event("message", def.getTaskCode(), def.getTaskTitle(), null, statusMsg, null, null));

          String content = executeTaskWithRetry(def, session, artifacts, model, systemPrompt);
          artifacts.put(def.getTaskCode(), content);
          persistence.markTaskDone(sessionId, def.getTaskCode(), content);

          if ("project_title".equals(def.getTaskCode())) {
            String title = extractTitle(content);
            persistence.updateProjectTitle(sessionId, title);
            session.setProjectTitle(title);
          }

          sendEvent(emitter, event("content", def.getTaskCode(), def.getTaskTitle(), null, null, content, null));
          sendEvent(emitter, event("task_done", def.getTaskCode(), def.getTaskTitle(), null, null, null, null));
          Thread.sleep(300);
        } catch (ServiceException e) {
          persistence.markTaskFailed(sessionId, def.getTaskCode(), e.getMessage());
          sendError(emitter, e.getMessage(), def.getTaskCode(), true);
          return;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          persistence.markSessionStopped(sessionId, def.getTaskCode());
          sendError(emitter, "任务已停止，可点击继续生成", def.getTaskCode(), true);
          return;
        }
      }

      DocumentAgentWordPackager.PackResult pack = packager.pack(
        session.getProjectTitle(),
        artifacts
      );
      persistence.markSessionCompleted(sessionId, session.getProjectTitle(), pack.downloadFileName(), pack.zipBase64());
      sendEvent(emitter, event("complete", "packaging", "整体打包", null, "所有任务已完成", null, Map.of(
        "projectTitle", session.getProjectTitle(),
        "downloadFileName", pack.downloadFileName(),
        "downloadBase64", pack.zipBase64()
      )));
      emitter.complete();
    } catch (ServiceException e) {
      sendError(emitter, e.getMessage(), currentTaskCode, StringUtils.isNotBlank(currentTaskCode));
    } catch (Exception e) {
      log.error("智能文档 Agent 执行失败, sessionId={}", sessionId, e);
      if (StringUtils.isNotBlank(currentTaskCode)) {
        persistence.markTaskFailed(sessionId, currentTaskCode, "文档生成失败");
      }
      sendError(emitter, "文档生成失败，请点击继续生成重试", currentTaskCode, true);
    } finally {
      runtime.running.set(false);
      runtime.future = null;
    }
  }

  private DocAgentTaskRecord toRecord(DocAgentTaskDef def, String content) {
    DocAgentTaskRecord record = new DocAgentTaskRecord();
    record.setTaskCode(def.getTaskCode());
    record.setTaskTitle(def.getTaskTitle());
    record.setToolName(def.getToolName());
    record.setContent(content);
    return record;
  }

  private void replayTaskProgress(SseEmitter emitter, DocAgentTaskRecord record, boolean withDelay) {
    sendEvent(emitter, event("task_start", record.getTaskCode(), record.getTaskTitle(), null, null, null, null));
    sendEvent(emitter, event("tool", record.getTaskCode(), record.getTaskTitle(), record.getToolName(), null, null, null));
    sendEvent(emitter, event("content", record.getTaskCode(), record.getTaskTitle(), null, null, record.getContent(), null));
    sendEvent(emitter, event("task_done", record.getTaskCode(), record.getTaskTitle(), null, null, null, null));
    if (withDelay) {
      try {
        Thread.sleep(80);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private String executeTaskWithRetry(
    DocAgentTaskDef def,
    DocAgentSession session,
    Map<String, String> artifacts,
    ChatModel model,
    String systemPrompt
  ) {
    ServiceException lastError = null;
    for (int attempt = 1; attempt <= TASK_MAX_RETRIES; attempt++) {
      try {
        String content = executeTask(def, session, artifacts, model, systemPrompt);
        if (!"PACKAGE".equals(def.getTaskKind()) && StringUtils.isBlank(content)) {
          throw new ServiceException("AI 未返回有效内容");
        }
        return content;
      } catch (ServiceException e) {
        lastError = e;
        log.warn("智能文档步骤失败 task={}, attempt={}/{}, msg={}", def.getTaskCode(), attempt, TASK_MAX_RETRIES, e.getMessage());
        if (attempt < TASK_MAX_RETRIES) {
          sleepQuietly(1000L * attempt);
        }
      }
    }
    throw new ServiceException("步骤「" + def.getTaskTitle() + "」失败：" + (lastError != null ? lastError.getMessage() : "请稍后重试"));
  }

  private void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ServiceException("任务已中断");
    }
  }

  private String executeTask(
    DocAgentTaskDef def,
    DocAgentSession session,
    Map<String, String> artifacts,
    ChatModel model,
    String systemPrompt
  ) {
    try {
      return switch (def.getTaskKind()) {
        case "LLM" -> model.chat(systemPrompt + "\n\n" + buildUserPrompt(def.getTaskCode(), session, artifacts));
        case "DIAGRAM" -> model.chat(systemPrompt + "\n\n" + buildDiagramPrompt(def.getTaskCode(), session, artifacts));
        case "SQL" -> model.chat(systemPrompt + "\n\n" + buildSqlPrompt(session, artifacts));
        case "PACKAGE" -> "正在将各章节、图表与 SQL 脚本打包为 Word 文档压缩包...";
        default -> throw new ServiceException("未知任务类型: " + def.getTaskKind());
      };
    } catch (ServiceException e) {
      throw e;
    } catch (Exception e) {
      log.warn("智能文档步骤 AI 调用异常 task={}", def.getTaskCode(), e);
      throw new ServiceException("AI 调用失败，请稍后重试");
    }
  }

  private String buildUserPrompt(String taskCode, DocAgentSession session, Map<String, String> artifacts) {
    String ctx = buildContext(session, artifacts);
    return switch (taskCode) {
      case "input_summary" -> """
        根据以下用户需求，输出项目摘要（200-400字），包含技术栈、核心功能与目标用户。
        用户需求：
        %s
        """.formatted(session.getDescription());
      case "project_title" -> """
        根据需求生成论文标题，输出两行：
        中文标题：（一行）
        英文标题：（一行）
        需求与摘要：
        %s
        """.formatted(ctx);
      case "functional_requirements" -> """
        列出系统功能要点，分模块编号（FR-01 起），每项含名称与说明，使用 Markdown 列表。
        %s
        """.formatted(ctx);
      case "abstract_write" -> """
        撰写论文摘要总结：先中文摘要（3段），再英文摘要（对应翻译）。学术风格，约 500-800 中文字。
        %s
        """.formatted(ctx);
      case "keywords" -> """
        提取 3-5 个关键词，格式：
        中文关键词：词1；词2；词3
        英文关键词：word1; word2; word3
        %s
        """.formatted(ctx);
      case "references" -> """
        生成 8-12 条参考文献，GB/T 7714 格式，含期刊、学位论文、技术书籍，与项目技术相关。
        %s
        """.formatted(ctx);
      case "chapter1" -> chapterPrompt("第一章 绪论", "研究背景、意义、国内外研究现状、论文结构", session, artifacts);
      case "chapter2" -> chapterPrompt("第二章 关键技术介绍", "介绍项目所用关键技术（框架、数据库、中间件等）", session, artifacts);
      case "chapter3" -> chapterPrompt("第三章 需求分析", "可行性分析、功能需求、非功能需求、用例概述", session, artifacts);
      case "chapter4" -> chapterPrompt("第四章 系统设计", "总体架构、功能模块划分、接口设计", session, artifacts);
      case "chapter5" -> chapterPrompt("第五章 数据库设计", "概念设计、逻辑设计、表结构说明", session, artifacts);
      case "chapter6" -> chapterPrompt("第六章 系统实现", "开发环境、核心功能界面与实现要点", session, artifacts);
      case "chapter7" -> chapterPrompt("第七章 系统测试", "测试环境、功能测试用例表、测试结果分析", session, artifacts);
      case "conclusion" -> """
        撰写「总结与展望」：总结工作成果、不足与未来改进方向，约 400-600 字。
        %s
        """.formatted(ctx);
      case "acknowledgement" -> """
        撰写论文致谢，语气诚恳，约 200-300 字。
        %s
        """.formatted(ctx);
      default -> ctx;
    };
  }

  private String chapterPrompt(String chapter, String focus, DocAgentSession session, Map<String, String> artifacts) {
    return """
      撰写论文章节「%s」，%s。
      使用 Markdown 标题与小节（##、###），内容充实，约 800-1200 字。
      项目背景：
      %s
      """.formatted(chapter, focus, buildContext(session, artifacts));
  }

  private String buildDiagramPrompt(String taskCode, DocAgentSession session, Map<String, String> artifacts) {
    String type = switch (taskCode) {
      case "func_module_diagram" -> "功能模块图（flowchart TB，展示各功能模块层级关系）";
      case "architecture_diagram" -> "系统架构图（flowchart TB，展示前端、后端、数据库层）";
      case "er_diagram" -> "ER 图说明（用 Mermaid erDiagram 语法描述主要实体及关系，附简短说明）";
      default -> "图表";
    };
    return """
      仅输出 Mermaid 代码块内容（不要多余解释），用于 %s。
      项目信息：
      %s
      """.formatted(type, buildContext(session, artifacts));
  }

  private String buildSqlPrompt(DocAgentSession session, Map<String, String> artifacts) {
    return """
      根据项目需求生成 MySQL 建表 SQL（CREATE TABLE），含主键、必要索引与外键，表名小写下划线。
      仅输出 SQL，不要 markdown 代码块标记。
      项目信息：
      %s
      """.formatted(buildContext(session, artifacts));
  }

  private String buildContext(DocAgentSession session, Map<String, String> artifacts) {
    StringBuilder sb = new StringBuilder();
    sb.append("原始需求：\n").append(session.getDescription()).append("\n");
    appendArtifact(sb, artifacts, "input_summary", "项目摘要");
    appendArtifact(sb, artifacts, "project_title", "项目标题");
    appendArtifact(sb, artifacts, "functional_requirements", "功能要点");
    return sb.toString();
  }

  private void appendArtifact(StringBuilder sb, Map<String, String> artifacts, String key, String label) {
    String val = artifacts.get(key);
    if (StringUtils.isNotBlank(val)) {
      sb.append("\n").append(label).append("：\n").append(val).append("\n");
    }
  }

  private String extractTitle(String content) {
    if (content == null) {
      return "智能文档";
    }
    for (String line : content.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("中文标题")) {
        int idx = trimmed.indexOf('：');
        if (idx < 0) {
          idx = trimmed.indexOf(':');
        }
        if (idx >= 0 && idx + 1 < trimmed.length()) {
          return trimmed.substring(idx + 1).trim();
        }
      }
    }
    String first = content.lines().findFirst().orElse("智能文档").trim();
    return first.length() > 50 ? first.substring(0, 50) : first;
  }

  private void sendEvent(SseEmitter emitter, Map<String, Object> payload) {
    try {
      emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    } catch (IOException e) {
      throw new ServiceException("推送事件失败");
    }
  }

  private void sendError(SseEmitter emitter, String message, String taskId, boolean resumable) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", "error");
      payload.put("content", message);
      payload.put("resumable", resumable);
      if (StringUtils.isNotBlank(taskId)) {
        payload.put("taskId", taskId);
      }
      emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
      emitter.complete();
    } catch (IOException ex) {
      emitter.completeWithError(ex);
    }
  }

  private Map<String, Object> event(
    String type,
    String taskId,
    String taskTitle,
    String toolName,
    String message,
    String content,
    Map<String, Object> extra
  ) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("type", type);
    if (taskId != null) {
      map.put("taskId", taskId);
    }
    if (taskTitle != null) {
      map.put("taskTitle", taskTitle);
    }
    if (toolName != null) {
      map.put("toolName", toolName);
    }
    if (message != null) {
      map.put("message", message);
    }
    if (content != null) {
      map.put("content", content);
    }
    if (extra != null) {
      map.putAll(extra);
    }
    return map;
  }

  private String loadSystemPrompt() {
    ChatPromptVo prompt = chatPromptService.queryByCode(PROMPT_CODE);
    if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
      return prompt.getPromptContent();
    }
    return """
      你是资深软件工程论文写作专家，擅长撰写毕业设计、实验报告与项目汇报文档。
      输出使用 Markdown，语言规范、结构清晰，内容贴合计算机类毕设场景。
      不要输出「作为AI」等自我指涉语句。
      """;
  }

  private String resolveModelName(String requestModel) {
    if (StringUtils.isNotBlank(requestModel)) {
      return requestModel;
    }
    String defaultModel = erDiagramProperties.getDefaultModel();
    if (StringUtils.isBlank(defaultModel)) {
      throw new ServiceException("未指定模型且未配置 chat.model.default-model");
    }
    return defaultModel;
  }

  private ChatModel buildModel(String modelName) {
    ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
    if (modelVo == null) {
      throw new ServiceException("模型不存在: " + modelName);
    }
    return OpenAiChatModel.builder()
      .baseUrl(modelVo.getApiHost())
      .apiKey(modelVo.getApiKey())
      .modelName(modelVo.getModelName())
      .build();
  }

  private static final class RuntimeState {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Future<?> future;
    private volatile String currentTaskCode;
  }
}
