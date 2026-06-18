package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * ER 图生成响应（陈氏表示法）
 */
@Data
@Builder
public class ErDiagramResponse {

    /** 总体 E-R 图（仅实体 + 关系，不含属性） */
    private List<ErNodeVo> nodes;

    private List<ErEdgeVo> edges;

    /** 各实体属性图 */
    private List<ErEntityAttributeDiagramVo> attributeDiagrams;

    /** 实体及属性元数据 */
    private List<ErEntityMetaVo> entities;
}
