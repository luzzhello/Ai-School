package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 思维导图生成响应
 */
@Data
@Builder
public class MindMapResponse {

    private List<MindMapNodeVo> nodes;

    private String rootId;
}
