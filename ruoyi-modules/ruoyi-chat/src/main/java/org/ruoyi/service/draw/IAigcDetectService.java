package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.AigcDetectRequest;
import org.ruoyi.domain.dto.request.AigcDetectSegmentRequest;
import org.ruoyi.domain.vo.draw.AigcDetectReportDetailVo;
import org.ruoyi.domain.vo.draw.AigcDetectReportSummaryVo;
import org.ruoyi.domain.vo.draw.AigcDetectResultVo;
import org.ruoyi.domain.vo.draw.AigcDetectSegmentResultVo;

import java.util.List;

/**
 * 论文 AIGC 检测
 */
public interface IAigcDetectService {

    AigcDetectResultVo detect(AigcDetectRequest request);

    AigcDetectSegmentResultVo detectSegment(AigcDetectSegmentRequest request);

    List<AigcDetectReportSummaryVo> listReports(Integer limit);

    AigcDetectReportDetailVo getReport(String reportId);

    void deleteReport(String reportId);
}
