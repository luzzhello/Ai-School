package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemArchitectureGenerateRequest {

    @NotBlank(message = "系统架构描述不能为空")
    private String description;

    /** 架构图类型：type1 | type2 | type3 */
    private String archType;

    private String model;
}
