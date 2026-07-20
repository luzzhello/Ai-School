package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文生成智能体——生成参考文献请求体。
 */
@Data
public class PaperReferencesRequest {

    /** 会话 id */
    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    /** 指定模型（可选，缺省用 chat.model.default-model） */
    private String model;

    /** 检索关键词（可选，缺省用会话论文题目） */
    private String keyword;

    /** 语言筛选：zh / en，空表示中英文混合 */
    private String language;

    /** 期望条数（可选，默认 50，上限 50） */
    private Integer count;
}
