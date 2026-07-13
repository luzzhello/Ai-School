package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文生成智能体——解析 SQL 请求体。
 */
@Data
public class PaperParseSqlRequest {

    /** 会话 id */
    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    /** .sql 文件内容 */
    @NotBlank(message = "SQL 内容不能为空")
    private String sqlContent;
}
