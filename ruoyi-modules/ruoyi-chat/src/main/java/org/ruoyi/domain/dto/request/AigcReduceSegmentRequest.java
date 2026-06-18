package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AigcReduceSegmentRequest {

    @NotBlank(message = "片段内容不能为空")
    private String text;

    /** 是否在改写前调用 AI 检测 */
    private Boolean detectBefore = true;

    /** 是否在改写后调用 AI 检测 */
    private Boolean detectAfter = true;

    private String model;
}
