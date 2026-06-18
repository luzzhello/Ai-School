package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.AigcReduceSegmentRequest;
import org.ruoyi.domain.dto.request.AigcSplitRequest;
import org.ruoyi.domain.vo.draw.AigcReduceSegmentResultVo;
import org.ruoyi.domain.vo.draw.AigcSplitResultVo;
import org.ruoyi.service.draw.IAigcReduceService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 论文 AIGC 率降低（按片段）
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/aigc-reduce")
public class AigcReduceController {

    private final IAigcReduceService aigcReduceService;

    @PostMapping("/split")
    public R<AigcSplitResultVo> split(@RequestBody @Valid AigcSplitRequest request) {
        return R.ok(aigcReduceService.split(request));
    }

    /** 上传 DOCX/TXT 按 Word 标题 / 章节编号解析目录分段（推荐） */
    @PostMapping(value = "/split/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AigcSplitResultVo> splitFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "splitMode", defaultValue = "outline") String splitMode) {
        return R.ok(aigcReduceService.splitFromFile(file, splitMode));
    }

    @PostMapping("/segment")
    public R<AigcReduceSegmentResultVo> reduceSegment(@RequestBody @Valid AigcReduceSegmentRequest request) {
        return R.ok(aigcReduceService.reduceSegment(request));
    }
}
