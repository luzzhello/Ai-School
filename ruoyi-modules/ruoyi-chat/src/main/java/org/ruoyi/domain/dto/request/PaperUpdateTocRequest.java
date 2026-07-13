package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.ruoyi.domain.paper.TocNode;

import java.util.List;

/**
 * 论文生成智能体——保存/更新目录大纲。
 */
@Data
public class PaperUpdateTocRequest {

    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    @NotNull(message = "目录不能为空")
    private List<TocNode> toc;
}
