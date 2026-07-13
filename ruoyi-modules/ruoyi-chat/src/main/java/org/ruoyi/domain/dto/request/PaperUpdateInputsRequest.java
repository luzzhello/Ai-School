package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文生成智能体——更新会话输入（题目、字数、专业、代码等）。
 */
@Data
public class PaperUpdateInputsRequest {

    /** 会话 id */
    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    /** 论文题目 */
    private String title;

    /** Controller/Service 代码内容（可选） */
    private String codeContent;

    /** 开发环境信息 */
    private String envInfo;

    /** 字数要求 */
    private Integer wordCount;

    /** 学历层次：本科 / 专科 */
    private String educationLevel;
}
