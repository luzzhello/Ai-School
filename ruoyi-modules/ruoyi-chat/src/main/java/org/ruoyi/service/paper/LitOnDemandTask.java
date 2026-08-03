package org.ruoyi.service.paper;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 按需文献抓取任务状态。
 */
@Data
public class LitOnDemandTask {

    public static final class Status {

        public static final String PENDING = "pending";
        public static final String RUNNING = "running";
        public static final String DONE = "done";
        public static final String PARTIAL = "partial";
        public static final String FAILED = "failed";

        private Status() {
        }
    }

    private String taskId;

    private String sessionId;

    private String title;

    private String outlineStatus = Status.PENDING;

    private String litStatus = Status.PENDING;

    private List<String> keywords = new ArrayList<>();

    private int fetchedCount;

    /** 新入库中文条数 */
    private int fetchedCountZh;

    /** 新入库英文条数 */
    private int fetchedCountEn;

    /** db = 库内直选；crawl = 爬取路径；未判定前可为 null */
    private String source;

    private int selectedCountZh;

    private int selectedCountEn;

    private Long userId;

    private String error;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = createdAt;
}
