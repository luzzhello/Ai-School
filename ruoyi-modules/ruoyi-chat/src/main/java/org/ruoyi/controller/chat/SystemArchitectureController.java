package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.SystemArchitectureGenerateRequest;
import org.ruoyi.domain.dto.response.SystemArchitectureResponse;
import org.ruoyi.service.draw.ISystemArchitectureService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统架构图生成
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/system-architecture")
public class SystemArchitectureController {

    private final ISystemArchitectureService systemArchitectureService;

    @PostMapping("/generate")
    public R<SystemArchitectureResponse> generate(@RequestBody @Valid SystemArchitectureGenerateRequest request) {
        return R.ok(systemArchitectureService.generate(request));
    }
}
