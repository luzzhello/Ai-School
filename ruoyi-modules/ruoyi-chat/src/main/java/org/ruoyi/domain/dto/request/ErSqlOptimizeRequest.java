package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ER 图 SQL AI 优化请求
 */
@Data
public class ErSqlOptimizeRequest {

    @NotBlank(message = "SQL 语句不能为空")
    private String sql;

    /**
     * 使用的对话模型名称（可选，未传时使用 chat.er-diagram.default-model）
     */
    private String model;
}
