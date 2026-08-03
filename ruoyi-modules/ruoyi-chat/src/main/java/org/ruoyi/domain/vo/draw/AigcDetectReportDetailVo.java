package org.ruoyi.domain.vo.draw;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * AIGC 检测报告详情（含正文，便于回看后跳转降率）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AigcDetectReportDetailVo extends AigcDetectResultVo {

    /** 检测时使用的正文 */
    private String content;

    private Date createTime;
}
