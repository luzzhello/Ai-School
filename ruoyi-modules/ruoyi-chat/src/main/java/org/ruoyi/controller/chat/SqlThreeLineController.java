package org.ruoyi.controller.chat;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.dto.request.SqlThreeLineExportRequest;
import org.ruoyi.service.draw.ISqlThreeLineService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SQL 三线表 Word 导出
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/sql-three-line")
public class SqlThreeLineController {

    private final ISqlThreeLineService sqlThreeLineService;

    /**
     * 导出 Word：SQL 模式直接导出；AI 模式先生成 SQL 再导出
     */
    @PostMapping("/export")
    public void export(@RequestBody @Valid SqlThreeLineExportRequest request, HttpServletResponse response) {
        sqlThreeLineService.exportWord(request, response);
    }
}
