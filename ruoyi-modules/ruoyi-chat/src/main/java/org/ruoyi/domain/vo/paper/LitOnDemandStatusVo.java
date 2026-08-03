package org.ruoyi.domain.vo.paper;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 按需文献抓取任务状态视图。
 */
@Data
public class LitOnDemandStatusVo {

    private String taskId;

    private String sessionId;

    private String title;

    private String outlineStatus;

    private String litStatus;

    private List<String> keywords;

    private int fetchedCount;

    /** 新入库中文条数 */
    private int fetchedCountZh;

    /** 新入库英文条数 */
    private int fetchedCountEn;

    /** db = 库内直选；crawl = 爬取路径；未判定前可为 null */
    private String source;

    private int selectedCountZh;

    private int selectedCountEn;

    private String error;

    private Instant createdAt;

    private Instant updatedAt;
}
