package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AigcSplitRequest {

    @NotBlank(message = "论文内容不能为空")
    private String content;

    /** paragraph | sentence */
    @NotBlank(message = "分割方式不能为空")
    private String splitMode;
}
