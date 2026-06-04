package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.SoftwareDiagramGenerateRequest;
import org.ruoyi.domain.dto.response.SoftwareDiagramResponse;
import org.ruoyi.service.draw.ISoftwareDiagramService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 软件工程图生成（每种图表类型独立接口与提示词）
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/software-diagram")
public class SoftwareDiagramController {

    private final ISoftwareDiagramService softwareDiagramService;

    @PostMapping("/{diagramType}/generate")
    public R<SoftwareDiagramResponse> generate(
        @PathVariable String diagramType,
        @RequestBody @Valid SoftwareDiagramGenerateRequest request
    ) {
        return R.ok(softwareDiagramService.generate(diagramType, request));
    }
}
