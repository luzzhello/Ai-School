package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 功能结构图生成响应
 */
@Data
@Builder
public class FuncStructureResponse {

    private List<FuncStructureNodeVo> nodes;

    private String rootId;
}
