package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AigcReduceSegmentResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String originalText;

    private String reducedText;

    private Double beforeRate;

    private Double afterRate;

    private Integer costCoins;
}
