package org.ruoyi.service.draw.impl;

/**
 * 从 classpath / file 加载的参考图，供多模态模型使用。
 */
public record DrawReferenceImage(String base64Data, String mimeType) {
}
