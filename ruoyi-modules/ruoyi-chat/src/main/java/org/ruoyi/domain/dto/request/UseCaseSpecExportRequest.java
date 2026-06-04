package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用例说明文档 Word 导出请求
 */
@Data
public class UseCaseSpecExportRequest {

    /**
     * 模式：ai / manual
     */
    @NotBlank(message = "生成模式不能为空")
    private String mode = "ai";

    /**
     * AI 模式：用例需求描述
     */
    private String description;

    /**
     * 手动模式：用例说明内容
     */
    private UseCaseSpecData spec;

    /**
     * 对话模型（可选）
     */
    private String model;

    /**
     * 表题注章节号，如 3 → 表3-1
     */
    private Integer chapterNumber = 3;

    /**
     * 表序号
     */
    private Integer tableIndex = 1;
}
