package org.ruoyi.service.draw.impl;

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

@Component
@RequiredArgsConstructor
public class ThesisLlmSupport {

    private static final String PROMPT_REDUCE = "thesis_reduce";

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;

    public String reduceText(String text, String modelName) {
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("待改写文本不能为空");
        }
        String systemPrompt = loadPrompt(PROMPT_REDUCE, defaultReducePrompt());
        String userPrompt = "请对以下文本进行降重改写：\n\n" + text.trim();
        String response = invokeModel(systemPrompt, userPrompt, modelName);
        return cleanupReducedText(response);
    }

    private String invokeModel(String systemPrompt, String userPrompt, String modelName) {
        ChatModel model = buildModel(resolveModelName(modelName));
        return model.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)).aiMessage().text();
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

    private String defaultReducePrompt() {
        return """
            你是学术论文降重助手。请对用户文本进行同义改写以降低查重重复率，要求：
            1. 保持原意、术语准确性与论述逻辑；
            2. 调整句式、语序与用词，避免与原文高度雷同；
            3. 不增删核心信息，不改变数据与结论；
            4. 只输出改写后的文本，不要标题、引号或解释。
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
