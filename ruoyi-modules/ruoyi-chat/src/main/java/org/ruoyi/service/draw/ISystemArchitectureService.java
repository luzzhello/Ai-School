package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.SystemArchitectureGenerateRequest;
import org.ruoyi.domain.dto.response.SystemArchitectureResponse;

public interface ISystemArchitectureService {

    SystemArchitectureResponse generate(SystemArchitectureGenerateRequest request);
}
