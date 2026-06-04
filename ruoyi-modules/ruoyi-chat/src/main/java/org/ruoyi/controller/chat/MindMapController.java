package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.MindMapGenerateRequest;
import org.ruoyi.domain.dto.response.MindMapResponse;
import org.ruoyi.service.draw.IMindMapService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 思维导图生成
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/mind-map")
public class MindMapController {

    private final IMindMapService mindMapService;

    /**
     * 根据描述生成思维导图
     */
    @PostMapping("/generate")
    public R<MindMapResponse> generate(@RequestBody @Valid MindMapGenerateRequest request) {
        return R.ok(mindMapService.generate(request));
    }
}
