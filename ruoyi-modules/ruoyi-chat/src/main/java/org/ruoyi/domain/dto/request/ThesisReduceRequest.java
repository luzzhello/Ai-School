package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文降重请求
 */
@Data
public class ThesisReduceRequest {

    private String mode;

    @NotBlank(message = "论文标题不能为空")
    private String title;

    @NotBlank(message = "论文内容不能为空")
    private String content;

    @NotBlank(message = "分割方式不能为空")
    private String splitMode;
}
