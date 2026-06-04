package org.ruoyi.service.draw;

import jakarta.servlet.http.HttpServletResponse;
import org.ruoyi.domain.dto.request.UseCaseSpecExportRequest;

/**
 * 用例说明文档 Word 导出
 */
public interface IUseCaseSpecService {

    void exportWord(UseCaseSpecExportRequest request, HttpServletResponse response);
}
