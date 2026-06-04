package org.ruoyi.controller.chat;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.dto.request.FuncTestExportRequest;
import org.ruoyi.service.draw.IFuncTestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 功能测试文档 Word 导出
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/func-test")
public class FuncTestController {

    private final IFuncTestService funcTestService;

    @PostMapping("/export")
    public void export(@RequestBody @Valid FuncTestExportRequest request, HttpServletResponse response) {
        funcTestService.exportWord(request, response);
    }
}
