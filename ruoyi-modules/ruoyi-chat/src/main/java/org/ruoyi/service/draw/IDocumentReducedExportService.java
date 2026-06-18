package org.ruoyi.service.draw;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 在原上传 Word 文档中写回改写结果
 */
public interface IDocumentReducedExportService {

    void export(MultipartFile file, String title, List<String> segments, String splitMode, HttpServletResponse response);
}
