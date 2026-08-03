package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.ruoyi.domain.paper.PaperUiScreenshot;

import java.util.List;

/**
 * 论文生成智能体——AI 识别系统实现功能界面截图的功能名称。
 */
@Data
public class PaperScreenshotsAnalyzeRequest {

    /** 会话 id */
    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;

    /** 待识别项；为空则识别会话内当前保存的全部截图 */
    private List<PaperUiScreenshot> items;
}
