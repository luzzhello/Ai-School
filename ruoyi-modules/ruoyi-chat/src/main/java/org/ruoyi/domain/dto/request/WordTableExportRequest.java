package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Word 表格导出请求
 */
@Data
public class WordTableExportRequest {

    @NotBlank(message = "生成模式不能为空")
    private String mode = "ai";

    /** AI 模式：表格需求描述 */
    private String description;

    /** 表格标题（表上方居中） */
    private String title;

    private Integer rowCount = 4;
    private Integer colCount = 4;

    /** 表头列名 */
    private List<String> headers;

    /** 手动模式：数据行（不含表头） */
    private List<List<String>> rows;

    private WordTableBorderConfig headerBorder;
    private WordTableBorderConfig dataRowBorder;
    private WordTableBorderConfig lastRowBorder;

    private String model;
}
