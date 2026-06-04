package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 功能结构图生成请求
 */
@Data
public class FuncStructureGenerateRequest {

    /**
     * 系统描述（AI 模式）
     */
    private String description;

    /**
     * 生成模式：ai
     */
    @NotBlank(message = "生成模式不能为空")
    private String mode = "ai";

    /**
     * 使用的对话模型名称（可选）
     */
    private String model;
}
