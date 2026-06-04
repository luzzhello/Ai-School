package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ER 图生成请求
 */
@Data
public class ErDiagramGenerateRequest {

    /**
     * 系统描述（AI 模式）
     */
    private String description;

    /**
     * SQL 语句（SQL 模式）
     */
    private String sql;

    /**
     * 生成模式：ai / sql
     */
    @NotBlank(message = "生成模式不能为空")
    private String mode = "ai";

    /**
     * 使用的对话模型名称（可选，未传时使用配置文件 chat.er-diagram.default-model）
     */
    private String model;
}
