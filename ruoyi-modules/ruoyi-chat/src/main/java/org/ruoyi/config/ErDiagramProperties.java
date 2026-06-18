package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ER 图生成配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "chat.model")
public class ErDiagramProperties {

    /**
     * 默认对话模型名称（对应 chat_model.model_name）
     */
    private String defaultModel = "deepseek-v4-flash";
}
