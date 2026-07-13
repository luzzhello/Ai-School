package org.ruoyi.domain.paper;

import lombok.Data;

import java.io.Serializable;

/**
 * 论文会话列表项（摘要信息，不含正文）。
 */
@Data
public class PaperSessionSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;

    private String title;

    /** init / ref_confirmed / toc_confirmed / writing / done */
    private String status;

    private Long createTime;

    private Long updateTime;

    /** 已完成章节数 */
    private int chapterDone;

    /** 章节总数 */
    private int chapterTotal;
}
