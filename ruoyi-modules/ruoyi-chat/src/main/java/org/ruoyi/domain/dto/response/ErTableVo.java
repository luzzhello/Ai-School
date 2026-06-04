package org.ruoyi.domain.dto.response;

import lombok.Data;

import java.util.List;

/**
 * ER 图表节点
 */
@Data
public class ErTableVo {

    private String id;

    private String name;

    private String comment;

    private List<ErFieldVo> fields;

    private Double x;

    private Double y;
}
