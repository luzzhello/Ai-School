package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * ER 图 SQL AI 优化响应
 */
@Data
@Builder
public class ErSqlOptimizeResponse {

    /**
     * 优化后的完整 SQL
     */
    private String sql;
}
