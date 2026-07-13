package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.ruoyi.domain.paper.Reference;

import java.util.List;

/**
 * 论文生成智能体——确认（锁定）参考文献。
 */
@Data
public class PaperConfirmReferencesRequest {

    /** 会话 id */
    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    /** 用户确认后的最终文献列表 */
    private List<Reference> references;
}
