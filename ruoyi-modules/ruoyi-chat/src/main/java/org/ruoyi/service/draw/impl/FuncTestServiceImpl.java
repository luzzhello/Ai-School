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
import org.ruoyi.domain.dto.request.FuncTestCaseData;
import org.ruoyi.domain.dto.request.FuncTestExportRequest;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.IFuncTestService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FuncTestServiceImpl implements IFuncTestService {

    private static final String PROMPT_CODE = "func_test_doc";

    private static final String JSON_SCHEMA = """
        {
          "documentTitle": "管理员功能测试",
          "testCases": [
            {
              "caseId": "GA001",
              "caseName": "用例名称",
              "preconditions": "前置条件",
              "testSteps": "测试步骤（可多行）",
              "expectedResult": "预期结果",
              "testResult": "成功"
            }
          ]
        }
        """;

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;
    private final IFeatureCoinService featureCoinService;

    @Override
    public void exportWord(FuncTestExportRequest request, HttpServletResponse response) {
        validateRequest(request);
        String featureCode = isAiMode(request) ? FeatureCodes.FUNC_TEST_AI : FeatureCodes.FUNC_TEST_MANUAL;
        featureCoinService.requireAffordableForLoginUser(featureCode, null);
        try {
            String title;
            List<FuncTestCaseData> cases;
            if (isAiMode(request)) {
                ParsedDoc parsed = generateFromAi(request);
                title = StringUtils.isNotBlank(parsed.title()) ? parsed.title() : "功能测试";
                cases = parsed.cases();
            } else {
                title = resolveTitle(request);
                cases = request.getTestCases();
            }
            if (cases == null || cases.isEmpty()) {
                throw new ServiceException("未生成任何测试用例");
            }
            int chapter = request.getChapterNumber() != null ? request.getChapterNumber() : 1;
            int tableIndex = request.getTableIndex() != null ? request.getTableIndex() : 1;
            byte[] bytes = FuncTestWordExporter.export(title, cases, chapter, tableIndex);
            String filename = title + "功能测试.docx";
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            FileUtils.setAttachmentResponseHeader(response, filename);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
            featureCoinService.chargeForLoginUser(featureCode, null);
        } catch (ServiceException e) {
            throw e;
        } catch (IOException e) {
            log.error("导出功能测试 Word 失败", e);
            throw new ServiceException("导出 Word 失败");
        }
    }

    private void validateRequest(FuncTestExportRequest request) {
        if (isAiMode(request)) {
            if (StringUtils.isBlank(request.getDescription())) {
                throw new ServiceException("请输入功能测试文档需求");
            }
        } else {
            if (request.getTestCases() == null || request.getTestCases().isEmpty()) {
                throw new ServiceException("请至少添加一条测试用例");
            }
        }
    }

    private String resolveTitle(FuncTestExportRequest request) {
        if (StringUtils.isNotBlank(request.getDocumentTitle())) {
            return request.getDocumentTitle().trim();
        }
        if (!isAiMode(request) && request.getTestCases() != null && !request.getTestCases().isEmpty()) {
            return "功能测试";
        }
        return "功能测试";
    }

    private record ParsedDoc(String title, List<FuncTestCaseData> cases) {
    }

    private ParsedDoc generateFromAi(FuncTestExportRequest request) {
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String systemPrompt = loadSystemPrompt();
        String userPrompt = "功能测试需求：\n" + request.getDescription().trim()
            + "\n\n请输出完整功能测试 JSON，至少 3 条测试用例。";
        log.info("AI 生成功能测试文档, model={}", modelName);
        String raw = model.chat(systemPrompt + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + JSON_SCHEMA
            + "\n\n" + userPrompt);
        return parseDocJson(raw);
    }

    private ParsedDoc parseDocJson(String raw) {
        try {
            String candidate = raw == null ? "" : raw.trim();
            if (candidate.contains("{")) {
                int start = candidate.indexOf('{');
                int end = candidate.lastIndexOf('}');
                candidate = candidate.substring(start, end + 1);
            }
            JsonNode root = objectMapper.readTree(candidate);
            String title = root.path("documentTitle").asText("功能测试").trim();
            JsonNode arr = root.path("testCases");
            if (!arr.isArray() || arr.isEmpty()) {
                throw new ServiceException("AI 未生成测试用例");
            }
            List<FuncTestCaseData> cases = new ArrayList<>();
            int idx = 1;
            for (JsonNode node : arr) {
                FuncTestCaseData row = new FuncTestCaseData();
                row.setCaseId(textOrDefault(node, "caseId", "GA" + String.format("%03d", idx)));
                row.setCaseName(text(node, "caseName"));
                row.setPreconditions(text(node, "preconditions"));
                row.setTestSteps(text(node, "testSteps"));
                row.setExpectedResult(text(node, "expectedResult"));
                row.setTestResult(textOrDefault(node, "testResult", "成功"));
                if (StringUtils.isNotBlank(row.getCaseName())) {
                    cases.add(row);
                    idx++;
                }
            }
            if (cases.isEmpty()) {
                throw new ServiceException("AI 未生成有效测试用例");
            }
            return new ParsedDoc(title, cases);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析功能测试 JSON 失败: {}", raw, e);
            throw new ServiceException("AI 返回格式错误，未能生成功能测试文档");
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private String textOrDefault(JsonNode node, String field, String def) {
        String v = text(node, field);
        return StringUtils.isNotBlank(v) ? v : def;
    }

    private boolean isAiMode(FuncTestExportRequest request) {
        return "ai".equalsIgnoreCase(request.getMode());
    }

    private String loadSystemPrompt() {
        ChatPromptVo prompt = chatPromptService.queryByCode(PROMPT_CODE);
        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
            return prompt.getPromptContent();
        }
        return """
            你是软件测试专家。根据需求输出功能测试用例表 JSON。
            testCases 每条含 caseId、caseName、preconditions、testSteps、expectedResult、testResult（默认「成功」）。
            用例编号建议 GA001 格式。仅输出 JSON。
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
}
