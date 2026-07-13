package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.PaperAssetProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 论文插图等资源上传（默认本地存储，正文写入 /api/paper/assets/... URL）。
 */
@Service
@RequiredArgsConstructor
public class PaperAssetService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final String PUBLIC_PREFIX = "/api/paper/assets/";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PaperAssetProperties properties;

    @Value("${sys.upload.path:./upload}")
    private String sysUploadPath;

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (!isAllowedImageUpload(file.getOriginalFilename(), contentType)) {
            throw new ServiceException("仅支持上传图片文件（含 SVG）");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ServiceException("单张图片不能超过 5MB");
        }
        if (!"local".equalsIgnoreCase(StringUtils.defaultIfBlank(properties.getStorage(), "local"))) {
            throw new ServiceException("当前仅支持本地存储，请在 paper.asset.storage=local 下使用");
        }

        String ext = resolveExtension(file);
        String relativePath = LocalDate.now().format(DATE_FMT) + "/" + UUID.randomUUID() + ext;
        Path target = resolveBaseDir().resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new ServiceException("图片保存失败: " + e.getMessage());
        }
        return PUBLIC_PREFIX + relativePath.replace('\\', '/');
    }

    public Resource loadAsResource(String relativePath) {
        if (StringUtils.isBlank(relativePath)) {
            throw new ServiceException("资源路径无效");
        }
        String safe = sanitizeRelativePath(relativePath);
        Path file = resolveBaseDir().resolve(safe);
        if (!Files.isRegularFile(file)) {
            throw new ServiceException("图片不存在");
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return new ByteArrayResource(bytes);
        } catch (IOException e) {
            throw new ServiceException("读取图片失败: " + e.getMessage());
        }
    }

    public byte[] readAssetBytes(String srcOrRelative) {
        String relative = extractRelativePath(srcOrRelative);
        Path file = resolveBaseDir().resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw new ServiceException("图片不存在: " + relative);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ServiceException("读取图片失败: " + e.getMessage());
        }
    }

    public MediaType resolveMediaType(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml;charset=UTF-8");
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_PNG;
    }

    public String extractRelativePath(String srcOrRelative) {
        if (StringUtils.isBlank(srcOrRelative)) {
            throw new ServiceException("资源路径无效");
        }
        String value = srcOrRelative.trim();
        int idx = value.indexOf(PUBLIC_PREFIX);
        if (idx >= 0) {
            value = value.substring(idx + PUBLIC_PREFIX.length());
        }
        return sanitizeRelativePath(value);
    }

    private Path resolveBaseDir() {
        String configured = properties.getLocalDir();
        String base = StringUtils.isNotBlank(configured) ? configured : Paths.get(sysUploadPath, "paper").toString();
        return Paths.get(base).toAbsolutePath().normalize();
    }

    private String sanitizeRelativePath(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new ServiceException("非法资源路径");
        }
        return normalized;
    }

    private String resolveExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (StringUtils.isNotBlank(original) && original.contains(".")) {
            String ext = original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (ext.matches("\\.(png|jpe?g|gif|webp|svg)")) {
                return ext;
            }
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/svg+xml")) {
            return ".svg";
        }
        if ("image/jpeg".equals(contentType)) {
            return ".jpg";
        }
        if ("image/gif".equals(contentType)) {
            return ".gif";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".png";
    }

    private boolean isAllowedImageUpload(String originalFilename, String contentType) {
        if (StringUtils.isNotBlank(contentType)) {
            String lowerType = contentType.toLowerCase(Locale.ROOT);
            if (lowerType.startsWith("image/")) {
                return true;
            }
        }
        if (StringUtils.isBlank(originalFilename) || !originalFilename.contains(".")) {
            return false;
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return ext.matches("\\.(png|jpe?g|gif|webp|svg)");
    }
}
