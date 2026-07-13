package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文生成智能体——逐章生成请求体。
 */
@Data
public class PaperGenerateChapterRequest {

    /** 会话 id */
    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    /** 章节 id（与目录树节点 id 对应，如 abstract、ch1_1） */
    @NotBlank(message = "章节 id 不能为空")
    private String chapterId;

    /** 指定模型（可选，缺省用 chat.model.default-model） */
    private String model;
}
