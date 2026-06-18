package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.AigcSplitRequest;
import org.ruoyi.domain.dto.request.ThesisReduceRequest;
import org.ruoyi.domain.dto.request.ThesisReduceSegmentRequest;
import org.ruoyi.domain.vo.draw.AigcSplitResultVo;
import org.ruoyi.domain.vo.draw.ThesisReduceResultVo;
import org.ruoyi.domain.vo.draw.ThesisReduceSegmentResultVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 论文降重
 */
public interface IThesisReduceService {

    /** @deprecated 请使用 split + reduceSegment */
    ThesisReduceResultVo parse(ThesisReduceRequest request);

    AigcSplitResultVo split(AigcSplitRequest request);

    AigcSplitResultVo splitFromFile(MultipartFile file, String splitMode);

    ThesisReduceSegmentResultVo reduceSegment(ThesisReduceSegmentRequest request);
}
