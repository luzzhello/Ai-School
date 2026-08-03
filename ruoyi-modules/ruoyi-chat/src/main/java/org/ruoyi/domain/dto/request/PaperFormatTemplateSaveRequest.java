package org.ruoyi.domain.dto.request;

import lombok.Data;
import org.ruoyi.domain.paper.format.PaperFormatConfig;

/**
 * 论文排版模板新建 / 更新请求。
 */
@Data
public class PaperFormatTemplateSaveRequest {

    /** 模板名称 */
    private String name;

    /** 学校名称 */
    private String schoolName;

    /** 备注 */
    private String remark;

    /** 是否默认（仅新建时生效；更新请走 set-default） */
    private Integer isDefault;

    /** 状态：0 停用 / 1 启用 */
    private String status;

    /** 排版配置（稀疏覆盖） */
    private PaperFormatConfig format;
}
