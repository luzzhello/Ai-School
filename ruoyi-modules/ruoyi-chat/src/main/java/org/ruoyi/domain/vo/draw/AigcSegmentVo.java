package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AigcSegmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer index;

    /** 目录/章节标题，如 5.1.1 商品信息 */
    private String title;

    /** 标题层级，用于前端缩进展示 */
    private Integer level;

    private String text;

    private Integer wordCount;
}
