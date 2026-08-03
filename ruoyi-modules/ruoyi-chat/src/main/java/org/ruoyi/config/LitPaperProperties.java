package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文献库检索配置（lit_paper / lit_paper_en）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "paper.lit")
public class LitPaperProperties {

    /** 近 N 年，默认 5 */
    private int recentYears = 5;

    /**
     * 库内检索：题目/关键词分词后，每个词查询条数。
     */
    private int searchPerKeyword = 10;

    /**
     * 库内检索：分词结果合并去重后的总上限。
     */
    private int searchMaxTotal = 50;

    /** 按需抓取配置 */
    private OnDemand ondemand = new OnDemand();

    @Data
    public static class OnDemand {

        private boolean enabled = true;

        private int minKeywords = 3;

        private int maxKeywords = 5;

        private int maxPerKeyword = 20;

        private int taskTimeoutSec = 300;

        private boolean listOnly = true;

        private String pythonExecutable = "python";

        private String crawlerWorkDir = "tools/cnki-crawler";

        private String configPath = "config.yaml";

        /** 中/英库内检索各至少该条数才视为充足并跳过爬取 */
        private int dbReadyMinCount = 50;

        /** 库内充足时自动选用中文篇数 */
        private int autoSelectZh = 18;

        /** 库内充足时自动选用英文篇数 */
        private int autoSelectEn = 2;
    }
}
