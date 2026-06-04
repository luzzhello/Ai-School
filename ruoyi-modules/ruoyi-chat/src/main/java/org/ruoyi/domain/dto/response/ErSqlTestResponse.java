package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * SQL 解析预览（体验测试）
 */
@Data
@Builder
public class ErSqlTestResponse {

    private int tableCount;

    private List<String> tableLabels;

    private String message;
}
