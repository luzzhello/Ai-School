package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AIGC 检测段落块
 */
@Data
public class AigcDetectChunkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer index;

    /** 所属章节标题（仅展示，不参与检测文本） */
    private String heading;

    /** 章节层级：1=一级标题，2=二级…；0=未识别 */
    private Integer level;

    /** 该标题下的正文（不含标题行）；跳过段也可能有正文如目录条目 */
    private String text;

    private Integer wordCount;

    /** AIGC 概率 0-100；skipped=true 时为 null */
    private Double aigcRate;

    /** none | low | mid | high | skip（未检测：目录/标题占位/参考文献等） */
    private String riskLevel;

    /** true=不在检测范围，连续展示时灰色无底色 */
    private Boolean skipped;
}
