package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 画图类 AI 参考图配置。
 * <p>
 * 参考图按图表类型 key 配置（如 er、activity）。有参考图时自动附带 ImageContent；
 * 模型优先使用 {@code chat.diagram.vision-model}，未配置则回退到 {@code chat.model.default-model}
 * （如 kimi-k2.6 等原生多模态模型，无需单独配视觉模型）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chat.diagram")
public class DrawDiagramProperties {

    /**
     * 可选：有参考图时使用的模型（对应 chat_model.model_name）。
     * 未配置时使用 chat.model.default-model；若默认模型本身支持图文（如 kimi-k2.6），则无需配置此项。
     */
    private String visionModel;

    /**
     * 各图表类型参考图路径，如 er: classpath:draw-reference/er-chen-sample.png
     */
    private Map<String, String> referenceImages = new LinkedHashMap<>();

    public String resolveReferenceImage(String diagramKey) {
        if (referenceImages == null || diagramKey == null) {
            return null;
        }
        return referenceImages.get(diagramKey);
    }
}
