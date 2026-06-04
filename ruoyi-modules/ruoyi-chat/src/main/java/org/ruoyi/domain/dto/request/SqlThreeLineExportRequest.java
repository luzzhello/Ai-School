package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * SQL 三线表 Word 导出请求
 */
@Data
public class SqlThreeLineExportRequest {

    /**
     * 模式：sql / ai
     */
    @NotBlank(message = "生成模式不能为空")
    private String mode = "sql";

    /**
     * SQL 模式：建表语句
     */
    private String sql;

    /**
     * AI 模式：业务描述
     */
    private String description;

    /**
     * 对话模型（可选）
     */
    private String model;

    /**
     * 表格样式：normal / threeLine
     */
    private String tableStyle = "threeLine";

    /**
     * 类型大小写：upper / lower
     */
    private String typeCase = "upper";

    /**
     * 导出列
     */
    @NotEmpty(message = "请至少选择一列导出")
    private List<String> columns;

    /**
     * 导出模式：fullDocument / tablesOnly
     */
    private String docMode = "fullDocument";

    /**
     * 章节编号（完整文档表题注）
     */
    private Integer chapterNumber = 4;
}
