package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 功能测试文档 Word 导出请求
 */
@Data
public class FuncTestExportRequest {

    @NotBlank(message = "生成模式不能为空")
    private String mode = "ai";

    /** AI 模式：测试需求描述 */
    private String description;

    /** 表标题主题，如「管理员功能测试」 */
    private String documentTitle;

    /** 手动模式：测试用例行 */
    private List<FuncTestCaseData> testCases;

    private String model;

    private Integer chapterNumber = 1;

    private Integer tableIndex = 1;
}
