package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AIGC 检测结果
 */
@Data
public class AigcDetectResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String title;

    private Integer wordCount;

    /** AIGC 概率 0-100 */
    private Double aigcRate;

    /** 人工撰写概率 0-100 */
    private Double humanRate;

    private Integer costCoins;

    private String summary;
}
