package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.AigcDetectRequest;
import org.ruoyi.domain.dto.request.AigcDetectSegmentRequest;
import org.ruoyi.domain.vo.draw.AigcDetectReportDetailVo;
import org.ruoyi.domain.vo.draw.AigcDetectReportSummaryVo;
import org.ruoyi.domain.vo.draw.AigcDetectResultVo;
import org.ruoyi.domain.vo.draw.AigcDetectSegmentResultVo;
import org.ruoyi.service.draw.IAigcDetectService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/reports")
    public R<List<AigcDetectReportSummaryVo>> listReports(@RequestParam(required = false) Integer limit) {
        return R.ok(aigcDetectService.listReports(limit));
    }

    @GetMapping("/reports/{reportId}")
    public R<AigcDetectReportDetailVo> getReport(@PathVariable String reportId) {
        return R.ok(aigcDetectService.getReport(reportId));
    }

    @DeleteMapping("/reports/{reportId}")
    public R<Void> deleteReport(@PathVariable String reportId) {
        aigcDetectService.deleteReport(reportId);
        return R.ok();
    }
}
