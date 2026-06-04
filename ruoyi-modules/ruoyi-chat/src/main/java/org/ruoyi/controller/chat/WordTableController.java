package org.ruoyi.controller.chat;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.dto.request.WordTableExportRequest;
import org.ruoyi.service.draw.IWordTableService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Word 表格生成
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/word-table")
public class WordTableController {

    private final IWordTableService wordTableService;

    @PostMapping("/export")
    public void export(@RequestBody @Valid WordTableExportRequest request, HttpServletResponse response) {
        wordTableService.exportWord(request, response);
    }
}
