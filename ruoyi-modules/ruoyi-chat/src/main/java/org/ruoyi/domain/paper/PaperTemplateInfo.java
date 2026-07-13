package org.ruoyi.domain.paper;

import lombok.Builder;
import lombok.Data;

/**
 * 当前论文模板元信息（供管理端展示）。
 */
@Data
@Builder
public class PaperTemplateInfo {

    private String originalFilename;
    private long fileSize;
    private long updatedAt;
    private int headingCount;
    private PaperTemplateStyleMapping styles;
    private String docxPath;
    private String unpackedPath;
}
