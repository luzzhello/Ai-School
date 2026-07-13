package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文生成智能体——手动保存章节正文。
 */
@Data
public class PaperSaveChapterRequest {

    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    @NotBlank(message = "章节 id 不能为空")
    private String chapterId;

    private String content;
}
