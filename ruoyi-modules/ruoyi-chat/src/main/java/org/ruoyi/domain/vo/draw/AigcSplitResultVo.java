package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class AigcSplitResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String splitMode;

    private Integer segmentCount;

    private Integer wordCount;

    private List<AigcSegmentVo> segments;
}
