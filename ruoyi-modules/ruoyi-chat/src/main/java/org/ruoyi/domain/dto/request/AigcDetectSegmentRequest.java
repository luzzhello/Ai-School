package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AigcDetectSegmentRequest {

    @NotBlank(message = "片段内容不能为空")
    private String text;

    private String model;
}
