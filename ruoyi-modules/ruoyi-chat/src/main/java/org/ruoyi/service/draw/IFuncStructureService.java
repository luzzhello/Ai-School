package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.FuncStructureGenerateRequest;
import org.ruoyi.domain.dto.response.FuncStructureResponse;

/**
 * 功能结构图生成
 */
public interface IFuncStructureService {

    FuncStructureResponse generate(FuncStructureGenerateRequest request);
}
