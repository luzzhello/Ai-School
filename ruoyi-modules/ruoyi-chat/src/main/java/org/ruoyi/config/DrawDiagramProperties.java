package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 画图类 AI 参考图配置。
 * <p>
 * 参考图按图表类型 key 配置（如 er、activity）。有参考图时自动附带 ImageContent。
 * 视觉识别默认模型见 {@link ChatVisionProperties}（{@code chat.vision.default-model}），与文本/画图默认模型分离。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chat.diagram")
public class DrawDiagramProperties {

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
