package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;
import org.ruoyi.domain.paper.PaperUiScreenshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 论文截图视觉识别结果（含成功/失败统计，供前端准确提示）。
 */
@Data
@Builder
public class PaperScreenshotsAnalyzeResponse {

    @Builder.Default
    private List<PaperUiScreenshot> screenshots = new ArrayList<>();

    /** 参与识别的截图张数 */
    private int imageTotal;

    /** AI 返回有效 title/label 的张数 */
    private int successCount;

    /** 调用失败或结果为空的张数 */
    private int failCount;

    /** 视觉模型是否初始化成功 */
    private boolean modelReady;

    /** 实际使用的视觉模型名 */
    private String modelName;

    /** 给前端展示的摘要文案 */
    private String message;
}
