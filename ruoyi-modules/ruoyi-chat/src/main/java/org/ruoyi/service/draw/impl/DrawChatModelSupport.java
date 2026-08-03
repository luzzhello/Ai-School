package org.ruoyi.service.draw.impl;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;

import java.time.Duration;

/**
 * 画图类 AI 调用的统一模型构建与异常转换。
 * <p>
 * 文本/画图用 {@link #buildModel}；视觉识别用 {@link #buildVisionModel}，二者配置与解析互不干扰。
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DrawChatModelSupport {

    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(120);

    private static final Duration VISION_TIMEOUT = Duration.ofSeconds(180);

    /**
     * 文本 / 画图模型：{@code model} 请求字段使用 chat_model.model_name。
     */
    public static ChatModel buildModel(IChatModelService chatModelService, String modelName) {
        ChatModelVo modelVo = requireModel(chatModelService, modelName);
        return OpenAiChatModel.builder()
            .baseUrl(modelVo.getApiHost())
            .apiKey(modelVo.getApiKey())
            .modelName(modelVo.getModelName())
            .timeout(CHAT_TIMEOUT)
            .maxRetries(0)
            .build();
    }

    /**
     * 视觉识别模型：独立入口，可解析 remark 中的 {@code api_model:xxx}（如火山方舟官方 Model ID）。
     *
     * @param modelName 优先使用的模型名；为空时调用方应已填入 chat.vision.default-model
     */
    public static ChatModel buildVisionModel(IChatModelService chatModelService, String modelName) {
        ChatModelVo modelVo = requireModel(chatModelService, modelName);
        String apiModel = resolveVisionApiModelName(modelVo);
        log.debug("视觉模型构建 name={}, apiModel={}, host={}", modelVo.getModelName(), apiModel, modelVo.getApiHost());
        return OpenAiChatModel.builder()
            .baseUrl(modelVo.getApiHost())
            .apiKey(modelVo.getApiKey())
            .modelName(apiModel)
            .timeout(VISION_TIMEOUT)
            .maxRetries(0)
            .build();
    }

    private static ChatModelVo requireModel(IChatModelService chatModelService, String modelName) {
        if (StringUtils.isBlank(modelName)) {
            throw new ServiceException("模型名称不能为空");
        }
        ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
        if (modelVo == null) {
            throw new ServiceException("模型不存在: " + modelName);
        }
        return modelVo;
    }

    /**
     * 视觉模型 API 名：优先 remark 中的 {@code api_model:xxx}，否则用 model_name。
     */
    static String resolveVisionApiModelName(ChatModelVo modelVo) {
        String remark = modelVo.getRemark();
        if (remark != null) {
            for (String part : remark.split("[;\\n]")) {
                String token = part.trim();
                if (token.regionMatches(true, 0, "api_model:", 0, 10)) {
                    String apiModel = token.substring(10).trim();
                    if (!apiModel.isEmpty()) {
                        return apiModel;
                    }
                }
            }
        }
        return modelVo.getModelName();
    }

    public static String chat(ChatModel model, String prompt) {
        try {
            return model.chat(prompt);
        } catch (Exception e) {
            log.error("AI系统访问异常", e);
            throw toServiceException(e);
        }
    }

    /**
     * 系统提示 + 用户提示（论文摘要等场景）。
     */
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
        return new ServiceException(AI_BUSY_MESSAGE, e.getMessage());
    }
}
