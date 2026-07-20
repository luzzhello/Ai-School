package org.ruoyi.service.draw.impl;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;

import java.time.Duration;

/**
 * 画图类 AI 调用的统一模型构建与异常转换。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DrawChatModelSupport {

    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(120);

    public static ChatModel buildModel(IChatModelService chatModelService, String modelName) {
        ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
        if (modelVo == null) {
            throw new ServiceException("模型不存在: " + modelName);
        }
        return OpenAiChatModel.builder()
            .baseUrl(modelVo.getApiHost())
            .apiKey(modelVo.getApiKey())
            .modelName(modelVo.getModelName())
            .timeout(CHAT_TIMEOUT)
            .maxRetries(0)
            .build();
    }

    public static String chat(ChatModel model, String prompt) {
        try {
            return model.chat(prompt);
        }
        catch (Exception e) {
            throw toServiceException(e);
        }
    }

    /** 系统提示 + 用户提示（论文摘要等场景）。 */
    public static String chat(ChatModel model, String systemPrompt, String userPrompt) {
        String system = systemPrompt == null ? "" : systemPrompt.trim();
        String user = userPrompt == null ? "" : userPrompt.trim();
        if (system.isEmpty()) {
            return chat(model, user);
        }
        if (user.isEmpty()) {
            return chat(model, system);
        }
        return chat(model, system + "\n\n" + user);
    }

    private static final String AI_BUSY_MESSAGE = "AI系统访问繁忙，稍后再试";

    private static ServiceException toServiceException(Throwable e) {
        return new ServiceException(AI_BUSY_MESSAGE);
    }
}
