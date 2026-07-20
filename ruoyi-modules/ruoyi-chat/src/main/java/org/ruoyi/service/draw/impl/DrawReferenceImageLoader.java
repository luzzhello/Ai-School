package org.ruoyi.service.draw.impl;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.StringUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 从配置路径加载画图参考图（classpath: / file:）。
 */
@Slf4j
@Component
public class DrawReferenceImageLoader {

    private static final Map<String, String> EXT_MIME = Map.of(
        "png", "image/png",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "webp", "image/webp",
        "gif", "image/gif"
    );

    public Optional<DrawReferenceImage> load(String location) {
        if (StringUtils.isBlank(location)) {
            return Optional.empty();
        }
        String normalized = location.trim();
        if (!normalized.startsWith("classpath:") && !normalized.startsWith("file:")) {
            normalized = "classpath:" + normalized;
        }
        try {
            Resource resource = new DefaultResourceLoader().getResource(normalized);
            if (!resource.exists()) {
                log.warn("参考图资源不存在: {}", normalized);
                return Optional.empty();
            }
            byte[] bytes = resource.getInputStream().readAllBytes();
            if (bytes.length == 0) {
                log.warn("参考图为空: {}", normalized);
                return Optional.empty();
            }
            String mime = mimeFromLocation(normalized);
            return Optional.of(new DrawReferenceImage(Base64.getEncoder().encodeToString(bytes), mime));
        }
        catch (IOException e) {
            log.warn("加载参考图失败: {}", normalized, e);
            return Optional.empty();
        }
    }

    private static String mimeFromLocation(String location) {
        int dot = location.lastIndexOf('.');
        if (dot < 0) {
            return "image/png";
        }
        String ext = location.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXT_MIME.getOrDefault(ext, "image/png");
    }
}
