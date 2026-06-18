package org.ruoyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 课设代码模板配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "course-code")
public class CourseCodeProperties {

    /**
     * 本地模板目录（含 template-backend、template-frontend、README.md）。
     * 未配置或不存在时使用 classpath 内嵌 template.zip。
     */
    private String templateDir;
}
