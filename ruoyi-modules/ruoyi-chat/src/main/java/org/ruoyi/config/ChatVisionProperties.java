package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 视觉识别模型配置（与 {@code chat.model.default-model} 文本/画图默认模型分离）。
 * <p>
 * 用于论文截图识别等需要图文多模态的场景。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chat.vision")
public class ChatVisionProperties {

    /**
     * 默认视觉模型名称（对应 chat_model.model_name）。
     */
    private String defaultModel = "Doubao-Seed-2.0-mini";
}
