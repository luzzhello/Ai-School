package org.ruoyi.domain.dto.response;

import lombok.Data;

/**
 * 论文排版模板下拉选项（启用中）。
 */
@Data
public class PaperFormatTemplateOptionVo {

    private Long id;
    private String name;
    private Integer isDefault;
    private String schoolName;
}
