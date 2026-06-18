package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AIGC 率降低请求
 */
@Data
public class AigcReduceRequest {

    /** text | file */
    private String mode;

    @NotBlank(message = "论文标题不能为空")
    private String title;

    @NotBlank(message = "论文内容不能为空")
    private String content;

    /** paragraph | sentence */
    @NotBlank(message = "分割方式不能为空")
    private String splitMode;
}
