package org.ruoyi.service.draw.impl;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.core.utils.file.FileUtils;
import org.ruoyi.service.draw.IDocumentReducedExportService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentReducedExportServiceImpl implements IDocumentReducedExportService {

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Override
    public void export(MultipartFile file, String title, List<String> segments, String splitMode, HttpServletResponse response) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请上传原始 DOCX 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ServiceException("文件过大，请上传 20MB 以内的文档");
        }
        if (segments == null || segments.isEmpty()) {
            throw new ServiceException("改写内容为空，无法导出");
        }
        String fileName = StringUtils.trim(file.getOriginalFilename());
        if (StringUtils.isBlank(fileName)) {
            throw new ServiceException("文件名无效");
        }
        if (!"docx".equals(extractExtension(fileName))) {
            throw new ServiceException("仅支持在原 DOCX 文件中写回改写结果");
        }
        String mode = StringUtils.defaultIfBlank(splitMode, "paragraph");
        if (!"paragraph".equals(mode) && !"sentence".equals(mode)) {
            throw new ServiceException("分割方式不正确");
        }

        String safeTitle = sanitizeTitle(title);
        try {
            byte[] bytes = DocumentReducedPatcher.patchDocx(file.getInputStream(), segments, mode);
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            FileUtils.setAttachmentResponseHeader(response, safeTitle + "_改写.docx");
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        }
        catch (ServiceException e) {
            throw e;
        }
        catch (IOException e) {
            log.error("导出改写 DOCX 失败", e);
            throw new ServiceException("导出失败，请确认文件未损坏后重试");
        }
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private String sanitizeTitle(String title) {
        String safe = StringUtils.trim(title);
        if (StringUtils.isBlank(safe)) {
            safe = "论文";
        }
        return safe.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
