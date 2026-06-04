package org.ruoyi.domain.dto.response;

import lombok.Data;

/**
 * ER 图字段
 */
@Data
public class ErFieldVo {

    private String name;

    private String type;

    private Boolean primaryKey;

    private Boolean nullable;

    private String comment;
}
