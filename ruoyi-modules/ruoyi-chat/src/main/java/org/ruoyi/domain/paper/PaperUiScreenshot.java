package org.ruoyi.domain.paper;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统实现「功能」截图组（会话级清单，持久化至 paper_session.ui_screenshots_json）。
 * <p>
 * 一个功能对应一节目录（如 5.1.1 用户管理功能），其下可挂多张界面截图（列表/新增/详情等）。
 * 兼容旧数据：若仅有顶层 {@link #assetUrl} 而无 {@link #images}，加载后应迁移为单图 images。
 */
@Data
public class PaperUiScreenshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 稳定标识，如 uss_xxx */
    private String id;

    /** 所属模块：admin（管理员）/ user（用户） */
    private String module;

    /**
     * 旧版单图 URL（仅兼容反序列化；新数据请写入 {@link #images}）。
     * @deprecated 使用 images
     */
    @Deprecated
    private String assetUrl;

    /** AI 识别或用户编辑后的功能名（不含章节编号前缀） */
    private String title;

    /** 同模块内排序序号 */
    private Integer sort;

    /** 用户是否在预览列表中确认过 */
    private Boolean confirmed;

    /** 该功能下的多张界面截图（列表/新增/详情等） */
    private List<PaperUiScreenshotImage> images = new ArrayList<>();
}
