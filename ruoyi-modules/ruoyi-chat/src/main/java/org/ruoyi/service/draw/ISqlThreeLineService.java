package org.ruoyi.service.draw;

import jakarta.servlet.http.HttpServletResponse;
import org.ruoyi.domain.dto.request.SqlThreeLineExportRequest;

/**
 * SQL 三线表 Word 导出
 */
public interface ISqlThreeLineService {

    /**
     * 导出 Word（SQL 直出或 AI 生成 SQL 后导出）
     */
    void exportWord(SqlThreeLineExportRequest request, HttpServletResponse response);
}
