package org.ruoyi.domain.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 单个实体的属性图（教材图 4.7 风格：实体在下方，属性椭圆扇形分布）
 */
@Data
public class ErEntityAttributeDiagramVo {

    private String entityName;

    private String entityId;

    private List<ErNodeVo> nodes;

    private List<ErEdgeVo> edges;
}
