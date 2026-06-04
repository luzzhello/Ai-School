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

    private List<ErNodeVo> nodes;

    private List<ErEdgeVo> edges;
}
