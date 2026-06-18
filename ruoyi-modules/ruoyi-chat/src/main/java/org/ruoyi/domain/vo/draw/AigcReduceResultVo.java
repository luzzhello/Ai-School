package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AIGC 率降低结果
 */
@Data
public class AigcReduceResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String title;

    private Integer wordCount;

    private Double beforeRate;

    private Double afterRate;

    private Integer costCoins;

    private String splitMode;

    private Integer segmentCount;

    private String reducedContent;

    /** 改写后的分段列表，与处理时分段一一对应，供前端按原文件格式导出 */
    private java.util.List<String> reducedSegments;

    private String summary;
}
