package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.AigcDetectRequest;
import org.ruoyi.domain.dto.request.AigcDetectSegmentRequest;
import org.ruoyi.domain.vo.draw.AigcDetectResultVo;
import org.ruoyi.domain.vo.draw.AigcDetectSegmentResultVo;
import org.ruoyi.service.draw.IAigcDetectService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 论文 AIGC 检测
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/aigc-detect")
public class AigcDetectController {

    private final IAigcDetectService aigcDetectService;

    @PostMapping("/check")
    public R<AigcDetectResultVo> check(@RequestBody @Valid AigcDetectRequest request) {
        return R.ok(aigcDetectService.detect(request));
    }

    @PostMapping("/segment")
    public R<AigcDetectSegmentResultVo> detectSegment(@RequestBody @Valid AigcDetectSegmentRequest request) {
        return R.ok(aigcDetectService.detectSegment(request));
    }
}
