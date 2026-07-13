package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 论文 Word 模板存储配置（docx + 解压后的 unpacked 目录）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "paper.template")
public class PaperTemplateProperties {

    /**
     * 模板根目录，默认 ${sys.upload.path}/paper/template
     */
    private String localDir;
}
