package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.ruoyi.domain.paper.PaperUiScreenshot;

import java.util.List;

/**
 * 论文生成智能体——保存系统实现功能界面截图清单（整表覆盖）。
 */
@Data
public class PaperScreenshotsSaveRequest {

    /** 会话 id */
    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    /** 截图清单（管理员/用户两侧合并） */
    private List<PaperUiScreenshot> screenshots;
}
