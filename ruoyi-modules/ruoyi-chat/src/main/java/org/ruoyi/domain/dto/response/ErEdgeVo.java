package org.ruoyi.domain.dto.response;

import lombok.Data;

/**
 * ER 图连线
 */
@Data
public class ErEdgeVo {

    private String id;

    private String from;

    private String to;

    /**
     * 基数标注：1 / n 等
     */
    private String label;
}
