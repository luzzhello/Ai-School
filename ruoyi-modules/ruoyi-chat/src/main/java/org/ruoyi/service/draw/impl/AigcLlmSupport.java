package org.ruoyi.service.draw.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class AigcLlmSupport {

    private static final String PROMPT_DETECT = "aigc_detect";
    private static final String PROMPT_REDUCE = "aigc_reduce";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    /** 改写后相对改写前至少下降的参考分差；未达标则加严重试 */
    private static final double MIN_RATE_DROP = 8.0;

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;

    public double detectAigcRate(String text, String modelName) {
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("检测文本不能为空");
        }
        String systemPrompt = loadPrompt(PROMPT_DETECT, defaultDetectPrompt());
        String userPrompt = "请评估以下段落的 AIGC 生成概率（0-100）：\n\n" + text.trim();
        String response = invokeModel(systemPrompt, userPrompt, modelName);
        return parseAigcRate(response);
    }

    public String reduceText(String text, String modelName) {
        return reduceText(text, modelName, null);
    }

    /**
     * @param beforeRate 改写前参考 AIGC 率；传入时会按同一检测口径复检，未下降则加严重试
     */
    public String reduceText(String text, String modelName, Double beforeRate) {
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("待改写文本不能为空");
        }
        int sourceWords = countWords(text);
        double baseline = beforeRate != null ? beforeRate : detectAigcRate(text, modelName);

        String reduced = invokeReduce(text, modelName, sourceWords, ReduceMode.NORMAL, baseline);
        reduced = ensureLength(reduced, text, modelName, sourceWords, baseline);

        double afterRate = detectAigcRate(reduced, modelName);
        if (afterRate > baseline - MIN_RATE_DROP) {
            log.info("降 AIGC 未达标，加严重试。before={}, after={}", baseline, afterRate);
            String retry = invokeReduce(text, modelName, sourceWords, ReduceMode.ANTI_DETECT, baseline);
            retry = ensureLength(retry, text, modelName, sourceWords, baseline);
            double retryRate = detectAigcRate(retry, modelName);
            // 取更低参考率的一版；若都没降，仍返回加严版（更贴近去套话目标）
            if (retryRate <= afterRate) {
                reduced = retry;
                afterRate = retryRate;
            }
            if (afterRate >= baseline) {
                log.warn("降 AIGC 复检仍未下降。before={}, after={}", baseline, afterRate);
            }
        }
        return reduced;
    }

    private String ensureLength(String reduced, String source, String modelName, int sourceWords, double baseline) {
        if (countWords(reduced) >= (int) (sourceWords * 0.88)) {
            return reduced;
        }
        return invokeReduce(source, modelName, sourceWords, ReduceMode.STRICT_LENGTH, baseline);
    }

    private enum ReduceMode {
        NORMAL,
        STRICT_LENGTH,
        ANTI_DETECT
    }

    private String invokeReduce(String text, String modelName, int sourceWords, ReduceMode mode, double baseline) {
        String systemPrompt = loadPrompt(PROMPT_REDUCE, defaultReducePrompt());
        int minWords = (int) Math.ceil(sourceWords * 0.92);
        int maxWords = (int) Math.ceil(sourceWords * 1.08);
        String lengthRule = mode == ReduceMode.STRICT_LENGTH
            ? "【硬性要求】上次改写篇幅过短。本次必须在保持信息完整的前提下扩写至达标字数，禁止删减句子、段落或要点。"
            : "【篇幅要求】改写后字数须保持在 " + minWords + "～" + maxWords + " 字（原文约 " + sourceWords + " 字），允许 ±8% 波动。";

        String antiRule = mode == ReduceMode.ANTI_DETECT
            ? "【复检未降分】上一版按本系统检测口径复检后 AIGC 参考率仍约 "
                + clampRate(baseline) + "% 或降幅不足。本次必须更大幅度消解套话、整齐句式与空洞衔接，"
                + "目标是让同一检测口径下的参考率明显下降（至少降 " + (int) MIN_RATE_DROP + " 分），"
                + "宁可略显口语/不工整，也禁止写得更「流畅模板化」。\n"
            : "【降分目标】改写前参考 AIGC 率约 " + clampRate(baseline) + "%，改写必须针对本系统检测口径降分，"
                + "禁止只做同义替换却写得更顺、更像范文。\n";

        String userPrompt = lengthRule + "\n"
            + antiRule
            + "改写要点（与检测抬分项一一对应）：\n"
            + "1. 删改套话：综上所述、基于以上分析、具有重要意义、随着…不断发展、在此基础上、本文旨在 等；\n"
            + "2. 打散整齐句式与排比，长短句交错，减少「首先/其次/再次/最后」机械推进；\n"
            + "3. 少用因此、然而、此外、同时等连接词堆砌，改为更自然的衔接；\n"
            + "4. 保留并突出具体数据、图表/表号、步骤、条件与限定，避免空泛评价；\n"
            + "5. 不要概括、不要合并句子、不要删除信息点；不得改变事实与结论。\n\n"
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
            你是学术论文段落级 AIGC 风险评估助手，输出的是「像 AI 写作的概率」参考值，不是知网等学校官方检测结果。
            请针对当前段落评分（0-100），关注：
            1. 模板套话与连接词堆砌（综上所述、基于以上分析、具有重要意义等）应抬高分数；
            2. 句式过于整齐、空洞论证、缺少可核验细节应抬高分数；
            3. 含具体实验数据、图表/表号引用、代码细节、个人研究过程的段落应明显降低分数；
            4. 规范学术表述、教科书式定义本身不等于高 AIGC，人工论文常见 20～50；
            5. 仅当文风机械、模板化堆砌、缺乏具体信息时才给 70 以上；请客观、保守评分。
            仅输出 JSON：{"aigcRate": 数字}
            """;
    }

    private String defaultReducePrompt() {
        return """
            你是学术论文降 AIGC 改写助手。成功标准是：按本系统同一套 AIGC 检测口径复检时，参考分下降；失败标准是写得更顺、更模板、更空。
            必须消解检测抬分特征：套话、连接词堆砌、整齐排比、空洞评价；保留数据、图表引用、步骤与事实。
            硬性要求：
            1. 字数与原文接近（±8%），禁止压缩、概括、删句、删段；
            2. 保留全部术语、数据、结论、引用与逻辑顺序；
            3. 不得引入新观点，不得改变事实；
            4. 只输出改写后的正文，不要标题、引号、解释或 Markdown。
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
