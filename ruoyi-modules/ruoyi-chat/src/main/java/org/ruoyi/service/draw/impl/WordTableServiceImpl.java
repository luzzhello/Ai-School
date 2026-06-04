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
import org.ruoyi.domain.dto.request.WordTableExportRequest;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.IWordTableService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordTableServiceImpl implements IWordTableService {

    private static final String PROMPT_CODE = "word_table";

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void exportWord(WordTableExportRequest request, HttpServletResponse response) {
        validateRequest(request);
        try {
            int colCount = clamp(request.getColCount(), 1, 12);
            int rowCount = clamp(request.getRowCount(), 1, 50);

            String title;
            List<String> headers;
            List<List<String>> dataRows;

            if (isAiMode(request)) {
                ParsedTable parsed = generateFromAi(request, colCount, rowCount);
                title = StringUtils.isNotBlank(parsed.title()) ? parsed.title() : safeTitle(request);
                headers = WordTableWordExporter.normalizeHeaders(parsed.headers(), colCount);
                dataRows = WordTableWordExporter.normalizeRows(parsed.rows(), rowCount, colCount);
            } else {
                title = safeTitle(request);
                headers = WordTableWordExporter.normalizeHeaders(request.getHeaders(), colCount);
                dataRows = WordTableWordExporter.normalizeRows(request.getRows(), rowCount, colCount);
            }

            byte[] bytes = WordTableWordExporter.export(request, headers, dataRows);
            String filename = title + ".docx";
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            FileUtils.setAttachmentResponseHeader(response, filename);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (ServiceException e) {
            throw e;
        } catch (IOException e) {
            log.error("导出 Word 表格失败", e);
            throw new ServiceException("导出 Word 失败");
        }
    }

    private void validateRequest(WordTableExportRequest request) {
        if (isAiMode(request)) {
            if (StringUtils.isBlank(request.getDescription())) {
                throw new ServiceException("请描述表格需求");
            }
        }
    }

    private record ParsedTable(String title, List<String> headers, List<List<String>> rows) {
    }

    private ParsedTable generateFromAi(WordTableExportRequest request, int colCount, int rowCount) {
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String headerHint = request.getHeaders() != null
            ? String.join("、", request.getHeaders())
            : "";
        String userPrompt = """
            表格标题：%s
            行数（不含表头）：%d
            列数：%d
            建议表头：%s

            需求描述：
            %s
            """.formatted(
            safeTitle(request),
            rowCount,
            colCount,
            headerHint,
            request.getDescription().trim()
        );
        String schema = """
            {
              "title": "表格标题",
              "headers": ["列1", "列2"],
              "rows": [["单元格", "单元格"]]
            }
            """;
        log.info("AI 生成 Word 表格, model={}", modelName);
        String raw = model.chat(loadSystemPrompt() + "\n\n严格输出 JSON，不要 markdown，结构：\n" + schema + "\n\n" + userPrompt);
        return parseTableJson(raw);
    }

    private ParsedTable parseTableJson(String raw) {
        try {
            String candidate = raw == null ? "" : raw.trim();
            if (candidate.contains("{")) {
                int start = candidate.indexOf('{');
                int end = candidate.lastIndexOf('}');
                candidate = candidate.substring(start, end + 1);
            }
            JsonNode root = objectMapper.readTree(candidate);
            String title = root.path("title").asText("").trim();
            List<String> headers = new ArrayList<>();
            JsonNode headersNode = root.path("headers");
            if (headersNode.isArray()) {
                for (JsonNode h : headersNode) {
                    headers.add(h.asText("").trim());
                }
            }
            List<List<String>> rows = new ArrayList<>();
            JsonNode rowsNode = root.path("rows");
            if (rowsNode.isArray()) {
                for (JsonNode rowNode : rowsNode) {
                    List<String> line = new ArrayList<>();
                    if (rowNode.isArray()) {
                        for (JsonNode cell : rowNode) {
                            line.add(cell.asText("").trim());
                        }
                    }
                    if (!line.isEmpty()) {
                        rows.add(line);
                    }
                }
            }
            if (headers.isEmpty() && rows.isEmpty()) {
                throw new ServiceException("AI 未生成有效表格数据");
            }
            return new ParsedTable(title, headers, rows);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 Word 表格 JSON 失败: {}", raw, e);
            throw new ServiceException("AI 返回格式错误");
        }
    }

    private boolean isAiMode(WordTableExportRequest request) {
        return "ai".equalsIgnoreCase(request.getMode());
    }

    private String safeTitle(WordTableExportRequest request) {
        return StringUtils.isNotBlank(request.getTitle()) ? request.getTitle().trim() : "Word表格";
    }

    private int clamp(Integer v, int min, int max) {
        int n = v != null ? v : min;
        return Math.max(min, Math.min(max, n));
    }

    private String loadSystemPrompt() {
        ChatPromptVo prompt = chatPromptService.queryByCode(PROMPT_CODE);
        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
            return prompt.getPromptContent();
        }
        return "你是文档表格生成助手。根据需求输出 title、headers、rows 的 JSON，rows 为二维字符串数组。";
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
}
