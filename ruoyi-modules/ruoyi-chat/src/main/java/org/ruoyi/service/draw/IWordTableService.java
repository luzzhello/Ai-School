package org.ruoyi.service.draw;

import jakarta.servlet.http.HttpServletResponse;
import org.ruoyi.domain.dto.request.WordTableExportRequest;

/**
 * Word 表格导出
 */
public interface IWordTableService {

    void exportWord(WordTableExportRequest request, HttpServletResponse response);
}
