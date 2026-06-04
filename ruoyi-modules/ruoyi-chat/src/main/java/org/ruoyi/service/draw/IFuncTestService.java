package org.ruoyi.service.draw;

import jakarta.servlet.http.HttpServletResponse;
import org.ruoyi.domain.dto.request.FuncTestExportRequest;

/**
 * 功能测试文档 Word 导出
 */
public interface IFuncTestService {

    void exportWord(FuncTestExportRequest request, HttpServletResponse response);
}
