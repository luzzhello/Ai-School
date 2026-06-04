package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ErSqlTestRequest {

    @NotBlank(message = "SQL 语句不能为空")
    private String sql;
}
