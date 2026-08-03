package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AIGC 检测报告列表摘要
 */
@Data
public class AigcDetectReportSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String reportId;

    private String title;

    private Integer wordCount;

    private Double aigcRate;

    private Double humanRate;

    private Integer costCoins;

    private String summary;

    private Date createTime;
}
