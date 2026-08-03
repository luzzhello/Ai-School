package org.ruoyi.domain.paper;

import lombok.Data;

import java.io.Serializable;

/**
 * 某一系统实现功能下的单张界面截图（列表 / 新增 / 详情等）。
 */
@Data
public class PaperUiScreenshotImage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 稳定标识，如 usi_xxx */
    private String id;

    /** 上传资源相对 URL */
    private String assetUrl;

    /** 界面类型标签：列表 / 新增 / 编辑 / 详情 / 其他 */
    private String label;

    /** 同功能内排序 */
    private Integer sort;
}
