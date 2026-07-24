package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * AIGC 检测结果
 */
@Data
public class AigcDetectResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String title;

    private Integer wordCount;

    /** AIGC 概率 0-100（段落字数加权） */
    private Double aigcRate;

    /** 人工撰写概率 0-100 */
    private Double humanRate;

    private Integer costCoins;

    private String summary;

    /** 落库后的报告 ID，便于前端刷新历史 */
    private String reportId;

    /** 段落级检测明细 */
    private List<AigcDetectChunkVo> segments = new ArrayList<>();
}
