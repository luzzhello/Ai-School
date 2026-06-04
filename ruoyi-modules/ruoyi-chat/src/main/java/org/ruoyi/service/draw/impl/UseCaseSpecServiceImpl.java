package org.ruoyi.service.draw.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.core.utils.file.FileUtils;
import org.ruoyi.config.ErDiagramProperties;
import org.ruoyi.domain.dto.request.UseCaseSpecData;
import org.ruoyi.domain.dto.request.UseCaseSpecExportRequest;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.IUseCaseSpecService;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UseCaseSpecServiceImpl implements IUseCaseSpecService {

    private static final String PROMPT_CODE = "use_case_spec";

    private static final String JSON_SCHEMA = """
        {
          "useCaseName": "用例中文名称",
          "role": "参与者角色",
          "description": "用例说明一段话",
          "preconditions": "前置条件",
          "postconditions": "后置条件",
          "basicFlow": "1. 步骤一\\n2. 步骤二",
          "extensionFlow": "1. 扩展步骤",
          "exceptionFlow": "1. 异常步骤",
          "others": "无"
        }
        """;

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void exportWord(UseCaseSpecExportRequest request, HttpServletResponse response) {
        validateRequest(request);
        try {
            UseCaseSpecData spec = resolveSpec(request);
            int chapter = request.getChapterNumber() != null ? request.getChapterNumber() : 3;
            int tableIndex = request.getTableIndex() != null ? request.getTableIndex() : 1;
            byte[] bytes = UseCaseSpecWordExporter.export(spec, chapter, tableIndex);
            String filename = buildFilename(spec);
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            FileUtils.setAttachmentResponseHeader(response, filename);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (ServiceException e) {
            throw e;
        } catch (IOException e) {
            log.error("导出用例说明 Word 失败", e);
            throw new ServiceException("导出 Word 失败");
        }
    }

    private void validateRequest(UseCaseSpecExportRequest request) {
        if (isAiMode(request)) {
            if (StringUtils.isBlank(request.getDescription())) {
                throw new ServiceException("请简述用例需求");
            }
        } else if (request.getSpec() == null || StringUtils.isBlank(request.getSpec().getUseCaseName())) {
            throw new ServiceException("请填写用例名称");
        }
    }

    private UseCaseSpecData resolveSpec(UseCaseSpecExportRequest request) {
        if (isAiMode(request)) {
            return generateFromAi(request);
        }
        return request.getSpec();
    }

    private UseCaseSpecData generateFromAi(UseCaseSpecExportRequest request) {
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String systemPrompt = loadSystemPrompt();
        String userPrompt = "用例需求描述：\n" + request.getDescription().trim()
            + "\n\n请输出完整用例说明 JSON。";
        log.info("AI 生成用例说明文档, model={}", modelName);
        String raw = model.chat(systemPrompt + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + JSON_SCHEMA
            + "\n\n" + userPrompt);
        return parseSpecJson(raw);
    }

    private UseCaseSpecData parseSpecJson(String raw) {
        try {
            String candidate = raw == null ? "" : raw.trim();
            if (candidate.contains("{")) {
                int start = candidate.indexOf('{');
                int end = candidate.lastIndexOf('}');
                candidate = candidate.substring(start, end + 1);
            }
            JsonNode root = objectMapper.readTree(candidate);
            UseCaseSpecData spec = new UseCaseSpecData();
            spec.setUseCaseName(text(root, "useCaseName"));
            spec.setRole(text(root, "role"));
            spec.setDescription(text(root, "description"));
            spec.setPreconditions(text(root, "preconditions"));
            spec.setPostconditions(text(root, "postconditions"));
            spec.setBasicFlow(flowText(root, "basicFlow"));
            spec.setExtensionFlow(flowText(root, "extensionFlow"));
            spec.setExceptionFlow(flowText(root, "exceptionFlow"));
            spec.setOthers(text(root, "others"));
            if (StringUtils.isBlank(spec.getUseCaseName())) {
                throw new ServiceException("AI 未生成有效用例名称");
            }
            return spec;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析用例说明 JSON 失败: {}", raw, e);
            throw new ServiceException("AI 返回格式错误，未能生成用例说明");
        }
    }

    private String text(JsonNode root, String field) {
        return root.path(field).asText("").trim();
    }

    private String flowText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (JsonNode item : node) {
                String line = item.asText("").trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                if (line.matches("^\\d+\\..*")) {
                    sb.append(line);
                } else {
                    sb.append(i++).append(". ").append(line);
                }
            }
            return sb.toString();
        }
        return node.asText("").trim();
    }

    private boolean isAiMode(UseCaseSpecExportRequest request) {
        return "ai".equalsIgnoreCase(request.getMode());
    }

    private String loadSystemPrompt() {
        ChatPromptVo prompt = chatPromptService.queryByCode(PROMPT_CODE);
        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
            return prompt.getPromptContent();
        }
        return """
            你是软件工程用例分析专家。根据用户业务描述，输出标准用例说明表 JSON。
            基本事件流、扩展流程、异常事件流使用换行分隔的编号步骤（如 1. xxx）。
            仅输出 JSON，不要解释。
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

    private String buildFilename(UseCaseSpecData spec) {
        String name = spec.getUseCaseName() != null ? spec.getUseCaseName().trim() : "用例说明";
        return name + "用例说明.docx";
    }
}
