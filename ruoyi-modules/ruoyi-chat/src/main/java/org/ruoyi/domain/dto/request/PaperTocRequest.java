package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文生成智能体——生成目录大纲请求体。
 */
@Data
public class PaperTocRequest {

    /** 会话 id */
    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    /** 指定模型（可选，缺省用 chat.model.default-model） */
    private String model;

    /** 为 true 时使用 resources/paper/thesis-template.docx 默认大纲模板 */
    private Boolean useDefaultTemplate;
}
