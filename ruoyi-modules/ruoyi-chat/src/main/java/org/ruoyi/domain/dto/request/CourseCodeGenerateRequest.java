package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 课设代码生成请求
 */
@Data
public class CourseCodeGenerateRequest {

    /** ai | sql */
    private String mode;

    @NotBlank(message = "生成内容不能为空")
    private String content;

    @NotBlank(message = "作者不能为空")
    private String author;
}
