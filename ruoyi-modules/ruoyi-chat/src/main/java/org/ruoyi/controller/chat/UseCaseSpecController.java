package org.ruoyi.controller.chat;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.dto.request.UseCaseSpecExportRequest;
import org.ruoyi.service.draw.IUseCaseSpecService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用例说明文档 Word 导出
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/use-case-spec")
public class UseCaseSpecController {

    private final IUseCaseSpecService useCaseSpecService;

    /**
     * 导出 Word：AI 生成用例说明 / 手动填写后导出
     */
    @PostMapping("/export")
    public void export(@RequestBody @Valid UseCaseSpecExportRequest request, HttpServletResponse response) {
        useCaseSpecService.exportWord(request, response);
    }
}
