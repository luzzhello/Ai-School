package org.ruoyi.domain.dto.response;

import lombok.Data;

/**
 * ER 图节点（陈氏表示法：entity/relationship/attribute）
 */
@Data
public class ErNodeVo {

    private String id;

    private String label;

    /**
     * entity | relationship | attribute
     */
    private String type;

    private Double x;

    private Double y;
}
