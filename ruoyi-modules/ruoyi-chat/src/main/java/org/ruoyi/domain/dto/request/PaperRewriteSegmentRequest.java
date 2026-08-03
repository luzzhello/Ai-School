package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文写作：选区文字改写（扩写 / 缩写 / 润色）
 */
@Data
public class PaperRewriteSegmentRequest {

    /** expand | shrink | polish */
    @NotBlank(message = "改写类型不能为空")
    private String mode;

    @NotBlank(message = "待改写文本不能为空")
    private String text;

    /** 润色时的用户提示词（可选） */
    private String prompt;

    private String model;
}
