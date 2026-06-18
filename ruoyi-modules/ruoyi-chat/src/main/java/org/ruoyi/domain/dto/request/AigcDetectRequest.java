package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AIGC 检测请求
 */
@Data
public class AigcDetectRequest {

    /** text | file */
    private String mode;

    @NotBlank(message = "论文标题不能为空")
    private String title;

    @NotBlank(message = "论文内容不能为空")
    private String content;
}
