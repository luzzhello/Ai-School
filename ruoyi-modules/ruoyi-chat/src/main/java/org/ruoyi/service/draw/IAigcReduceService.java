package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.AigcReduceSegmentRequest;
import org.ruoyi.domain.dto.request.AigcSplitRequest;
import org.ruoyi.domain.vo.draw.AigcReduceSegmentResultVo;
import org.ruoyi.domain.vo.draw.AigcSplitResultVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 论文 AIGC 率降低（按片段处理）
 */
public interface IAigcReduceService {

    AigcSplitResultVo split(AigcSplitRequest request);

    AigcSplitResultVo splitFromFile(MultipartFile file, String splitMode);

    AigcReduceSegmentResultVo reduceSegment(AigcReduceSegmentRequest request);
}
