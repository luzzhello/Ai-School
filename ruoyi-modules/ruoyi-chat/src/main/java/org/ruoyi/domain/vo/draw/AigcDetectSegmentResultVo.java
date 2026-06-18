package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AigcDetectSegmentResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Double aigcRate;

    private Double humanRate;

    private Integer costCoins;
}
