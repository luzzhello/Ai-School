package org.ruoyi.service.draw.impl;

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
import org.ruoyi.domain.dto.request.SqlThreeLineExportRequest;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.ISqlThreeLineService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlThreeLineServiceImpl implements ISqlThreeLineService {

    private static final String PROMPT_CODE = "sql_three_line";

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;

    @Override
    public void exportWord(SqlThreeLineExportRequest request, HttpServletResponse response) {
        validateRequest(request);
        try {
            String sql = resolveSql(request);
            List<SqlTableDocParser.SqlTableDef> tables = SqlTableDocParser.parse(sql);
            byte[] bytes = SqlThreeLineWordExporter.export(tables, request);
            String filename = buildFilename(request);
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            FileUtils.setAttachmentResponseHeader(response, filename);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (ServiceException e) {
            throw e;
        } catch (IOException e) {
            log.error("导出 Word 失败", e);
            throw new ServiceException("导出 Word 失败");
        }
    }

    private void validateRequest(SqlThreeLineExportRequest request) {
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw new ServiceException("请至少选择一列导出");
        }
        if (isSqlMode(request)) {
            if (StringUtils.isBlank(request.getSql())) {
                throw new ServiceException("请先输入有效的 CREATE TABLE SQL");
            }
        } else if (StringUtils.isBlank(request.getDescription())) {
            throw new ServiceException("请描述数据库表结构需求");
        }
    }

    private String resolveSql(SqlThreeLineExportRequest request) {
        if (isSqlMode(request)) {
            return request.getSql().trim();
        }
        return generateSqlFromAi(request);
    }

    private String generateSqlFromAi(SqlThreeLineExportRequest request) {
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String systemPrompt = loadSystemPrompt();
        String userPrompt = "业务场景描述：\n" + request.getDescription().trim()
            + "\n\n请输出完整 CREATE TABLE 建表 SQL。";
        log.info("AI 生成建表 SQL 并导出 Word, model={}", modelName);
        String raw = model.chat(systemPrompt + "\n\n" + userPrompt);
        String sql = extractSqlFromAiResponse(raw);
        if (StringUtils.isBlank(sql)) {
            throw new ServiceException("AI 未返回有效 SQL");
        }
        return sql;
    }

    private boolean isSqlMode(SqlThreeLineExportRequest request) {
        return "sql".equalsIgnoreCase(request.getMode());
    }

    private String extractSqlFromAiResponse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String candidate = raw.trim();
        if (candidate.contains("```")) {
            int start = candidate.indexOf("```");
            int lineEnd = candidate.indexOf('\n', start);
            int contentStart = lineEnd >= 0 ? lineEnd + 1 : start + 3;
            int end = candidate.indexOf("```", contentStart);
            if (end > contentStart) {
                candidate = candidate.substring(contentStart, end).trim();
            }
        }
        return candidate.trim();
    }

    private String loadSystemPrompt() {
        ChatPromptVo prompt = chatPromptService.queryByCode(PROMPT_CODE);
        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
            return prompt.getPromptContent();
        }
        return """
            你是 MySQL 数据库设计专家。根据用户业务描述输出 CREATE TABLE 建表 SQL。
            要求：多表、中文 COMMENT、表内外键、仅输出 SQL 无解释。
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

    private String buildFilename(SqlThreeLineExportRequest request) {
        if ("fullDocument".equalsIgnoreCase(request.getDocMode())) {
            return "数据库设计文档.docx";
        }
        if ("threeLine".equalsIgnoreCase(request.getTableStyle())) {
            return "三线表.docx";
        }
        return "普通表.docx";
    }
}
