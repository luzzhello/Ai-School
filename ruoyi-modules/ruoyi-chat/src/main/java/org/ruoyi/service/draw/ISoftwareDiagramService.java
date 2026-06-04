package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.SoftwareDiagramGenerateRequest;
import org.ruoyi.domain.dto.response.SoftwareDiagramResponse;

public interface ISoftwareDiagramService {

    SoftwareDiagramResponse generate(String diagramType, SoftwareDiagramGenerateRequest request);
}
