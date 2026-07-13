package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 论文插图存储配置（未配置 OSS 时使用本地目录）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "paper.asset")
public class PaperAssetProperties {

    /**
     * 存储方式：local（默认）| oss
     */
    private String storage = "local";

    /**
     * 本地存储根目录，默认 ${sys.upload.path}/paper
     */
    private String localDir;
}
