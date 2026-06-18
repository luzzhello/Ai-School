package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.AigcDetectRequest;
import org.ruoyi.domain.dto.request.AigcDetectSegmentRequest;
import org.ruoyi.domain.vo.draw.AigcDetectResultVo;
import org.ruoyi.domain.vo.draw.AigcDetectSegmentResultVo;

/**
 * 论文 AIGC 检测
 */
public interface IAigcDetectService {

    AigcDetectResultVo detect(AigcDetectRequest request);

    AigcDetectSegmentResultVo detectSegment(AigcDetectSegmentRequest request);
}
