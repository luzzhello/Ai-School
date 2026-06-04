package org.ruoyi.domain.dto.response;

import lombok.Data;

/**
 * ER 图关系
 */
@Data
public class ErRelationVo {

    private String id;

    private String from;

    private String to;

    /**
     * 关系类型：1:1 / 1:n / n:m
     */
    private String type;

    private String label;
}
