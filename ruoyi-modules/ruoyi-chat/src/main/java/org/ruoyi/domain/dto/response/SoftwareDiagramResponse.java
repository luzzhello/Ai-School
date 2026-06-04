package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 软件工程图生成响应（Mermaid 源码）
 */
@Data
@Builder
public class SoftwareDiagramResponse {

    private String mermaid;

    private String diagramType;
}
