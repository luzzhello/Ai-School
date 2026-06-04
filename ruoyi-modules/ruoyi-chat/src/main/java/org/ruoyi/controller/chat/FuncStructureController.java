package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.FuncStructureGenerateRequest;
import org.ruoyi.domain.dto.response.FuncStructureResponse;
import org.ruoyi.service.draw.IFuncStructureService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 功能结构图生成
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/func-structure")
public class FuncStructureController {

    private final IFuncStructureService funcStructureService;

    /**
     * 根据系统描述生成功能结构图
     */
    @PostMapping("/generate")
    public R<FuncStructureResponse> generate(@RequestBody @Valid FuncStructureGenerateRequest request) {
        return R.ok(funcStructureService.generate(request));
    }
}
