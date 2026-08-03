package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 论文排版模板（docx + format_json）本地存储配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "paper.format-template")
public class PaperFormatTemplateProperties {

    /**
     * 模板根目录，默认 ${sys.upload.path}/paper/format-templates
     */
    private String localDir;
}
