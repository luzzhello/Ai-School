package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.MindMapGenerateRequest;
import org.ruoyi.domain.dto.response.MindMapResponse;

/**
 * 思维导图生成服务
 */
public interface IMindMapService {

    MindMapResponse generate(MindMapGenerateRequest request);
}
