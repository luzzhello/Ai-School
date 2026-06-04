package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 软件工程图生成请求
 */
@Data
public class SoftwareDiagramGenerateRequest {

    @NotBlank(message = "图表描述不能为空")
    private String description;

    /** professional / modern */
    private String style = "professional";

    private String model;
}
