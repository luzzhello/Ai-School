package org.ruoyi.domain.dto.response;

import lombok.Data;
import org.ruoyi.domain.paper.format.PaperFormatConfig;

/**
 * 会话排版配置视图：模板、覆盖、合并结果与内置默认。
 */
@Data
public class PaperSessionFormatVo {

    /** 当前绑定的格式模板 id */
    private Long templateId;

    /** 会话级稀疏覆盖（无覆盖时为 null） */
    private PaperFormatConfig override;

    /** 默认 ← 模板 ← 覆盖 合并后的完整有效配置 */
    private PaperFormatConfig effective;

    /** 内置默认（大连海洋） */
    private PaperFormatConfig defaults;

    /** school | custom */
    private String mode;

    /** 是否已上传会话自定义 docx */
    private Boolean hasCustomDocx;

    /** 自定义 docx 原名 */
    private String customDocxName;

    /** 自定义模式是否强制 patch 样式 */
    private Boolean customPatchStyles;

    /** 自定义版式主配置（解析后） */
    private PaperFormatConfig customFormat;
}
