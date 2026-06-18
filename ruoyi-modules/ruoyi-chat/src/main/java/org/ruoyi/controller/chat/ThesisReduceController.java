package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.AigcSplitRequest;
import org.ruoyi.domain.dto.request.ThesisReduceRequest;
import org.ruoyi.domain.dto.request.ThesisReduceSegmentRequest;
import org.ruoyi.domain.vo.draw.AigcSplitResultVo;
import org.ruoyi.domain.vo.draw.ThesisReduceResultVo;
import org.ruoyi.domain.vo.draw.ThesisReduceSegmentResultVo;
import org.ruoyi.service.draw.IThesisReduceService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 论文降重（按片段）
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/thesis-reduce")
public class ThesisReduceController {

    private final IThesisReduceService thesisReduceService;

    /** 兼容旧版全文降重 */
    @PostMapping("/parse")
    public R<ThesisReduceResultVo> parse(@RequestBody @Valid ThesisReduceRequest request) {
        return R.ok(thesisReduceService.parse(request));
    }

    @PostMapping("/split")
    public R<AigcSplitResultVo> split(@RequestBody @Valid AigcSplitRequest request) {
        return R.ok(thesisReduceService.split(request));
    }

    @PostMapping(value = "/split/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AigcSplitResultVo> splitFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "splitMode", defaultValue = "outline") String splitMode) {
        return R.ok(thesisReduceService.splitFromFile(file, splitMode));
    }

    @PostMapping("/segment")
    public R<ThesisReduceSegmentResultVo> reduceSegment(@RequestBody @Valid ThesisReduceSegmentRequest request) {
        return R.ok(thesisReduceService.reduceSegment(request));
    }
}
