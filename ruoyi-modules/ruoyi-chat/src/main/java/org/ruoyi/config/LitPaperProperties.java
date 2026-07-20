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
}
