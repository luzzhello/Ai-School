package org.ruoyi.service.draw.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.ErDiagramProperties;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AigcLlmSupport {

    private static final String PROMPT_DETECT = "aigc_detect";
    private static final String PROMPT_REDUCE = "aigc_reduce";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;

    public double detectAigcRate(String text, String modelName) {
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("检测文本不能为空");
        }
        String systemPrompt = loadPrompt(PROMPT_DETECT, defaultDetectPrompt());
        String userPrompt = "请评估以下文本的 AIGC 生成概率（0-100）：\n\n" + text.trim();
        String response = invokeModel(systemPrompt, userPrompt, modelName);
        return parseAigcRate(response);
    }

    public String reduceText(String text, String modelName) {
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("待改写文本不能为空");
        }
        int sourceWords = countWords(text);
        String reduced = invokeReduce(text, modelName, sourceWords, false);
        if (countWords(reduced) < (int) (sourceWords * 0.88)) {
            reduced = invokeReduce(text, modelName, sourceWords, true);
        }
        return reduced;
    }

    private String invokeReduce(String text, String modelName, int sourceWords, boolean strictLength) {
        String systemPrompt = loadPrompt(PROMPT_REDUCE, defaultReducePrompt());
        int minWords = (int) Math.ceil(sourceWords * 0.92);
        int maxWords = (int) Math.ceil(sourceWords * 1.08);
        String lengthRule = strictLength
            ? "【硬性要求】上次改写篇幅过短。本次必须在保持信息完整的前提下扩写至达标字数，禁止删减句子、段落或要点。"
            : "【篇幅要求】改写后字数须保持在 " + minWords + "～" + maxWords + " 字（原文约 " + sourceWords + " 字），允许 ±8% 波动。";
        String userPrompt = lengthRule + "\n"
            + "请用同义替换、语序调整、长短句交错等方式改写，不要概括、不要合并句子、不要删除任何信息点。\n\n"
            + text.trim();
        String response = invokeModel(systemPrompt, userPrompt, modelName);
        return cleanupReducedText(response);
    }

    private static int countWords(String text) {
        return text == null ? 0 : text.replaceAll("\\s+", "").length();
    }

    private String invokeModel(String systemPrompt, String userPrompt, String modelName) {
        ChatModel model = buildModel(resolveModelName(modelName));
        return model.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)).aiMessage().text();
    }

    private double parseAigcRate(String response) {
        if (StringUtils.isBlank(response)) {
            throw new ServiceException("AI 检测未返回有效结果");
        }
        String trimmed = response.trim();
        try {
            JsonNode root = objectMapper.readTree(extractJson(trimmed));
            if (root.has("aigcRate")) {
                return clampRate(root.get("aigcRate").asDouble());
            }
            if (root.has("rate")) {
                return clampRate(root.get("rate").asDouble());
            }
        }
        catch (Exception ignored) {
            // fallback to regex
        }
        Matcher matcher = NUMBER_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return clampRate(Double.parseDouble(matcher.group(1)));
        }
        throw new ServiceException("无法解析 AIGC 检测结果");
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private double clampRate(double value) {
        return Math.max(0, Math.min(100, Math.round(value * 10.0) / 10.0));
    }

    private String cleanupReducedText(String response) {
        if (StringUtils.isBlank(response)) {
            throw new ServiceException("AI 改写未返回有效内容");
        }
        String text = response.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return text;
    }

    private String loadPrompt(String code, String fallback) {
        ChatPromptVo prompt = chatPromptService.queryByCode(code);
        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
            return prompt.getPromptContent();
        }
        return fallback;
    }

    private String defaultDetectPrompt() {
        return """
            你是学术论文 AIGC 风险评估助手，输出的是「像 AI 写作的概率」参考值，不是学校官方检测结果。
            评分原则：
            1. 含具体实验数据、图表引用、代码细节、个人研究过程的段落，应明显低于纯套话；
            2. 规范学术表述、教科书式定义不应直接判为高分，人工论文常见 20～50；
            3. 仅当文风机械、空洞、模板化堆砌、缺乏具体信息时，才给 70 以上；
            4. 请客观、保守评分，避免一律给高分。
            仅输出 JSON：{"aigcRate": 数字}
            """;
    }

    private String defaultReducePrompt() {
        return """
            你是学术论文降 AIGC 润色助手。目标：降低 AI 痕迹，同时保持篇幅与信息量基本不变。
            硬性要求：
            1. 字数与原文接近（±8%），禁止压缩、概括、删句、删段；
            2. 保留全部术语、数据、结论、引用与逻辑顺序；
            3. 通过同义替换、主被动转换、长短句重组、适度口语化学术表达来改写；
            4. 不得引入新观点，不得改变事实；
            5. 只输出改写后的正文，不要标题、引号、解释或 Markdown。
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
