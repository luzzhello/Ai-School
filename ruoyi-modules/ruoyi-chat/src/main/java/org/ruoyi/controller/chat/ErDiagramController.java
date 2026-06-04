package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.ErDiagramGenerateRequest;
import org.ruoyi.domain.dto.request.ErSqlOptimizeRequest;
import org.ruoyi.domain.dto.request.ErSqlTestRequest;
import org.ruoyi.domain.dto.response.ErDiagramResponse;
import org.ruoyi.domain.dto.response.ErSqlOptimizeResponse;
import org.ruoyi.domain.dto.response.ErSqlTestResponse;
import org.ruoyi.service.draw.IErDiagramService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ER 图生成
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/er")
public class ErDiagramController {

    private final IErDiagramService erDiagramService;

    /**
     * 根据系统描述或 SQL 生成 ER 图
     */
    @PostMapping("/generate")
    public R<ErDiagramResponse> generate(@RequestBody @Valid ErDiagramGenerateRequest request) {
        return R.ok(erDiagramService.generate(request));
    }

    /**
     * 体验测试 SQL 解析（不调用 AI）
     */
    @PostMapping("/test-sql")
    public R<ErSqlTestResponse> testSql(@RequestBody @Valid ErSqlTestRequest request) {
        return R.ok(erDiagramService.testSql(request.getSql()));
    }

    /**
     * AI 优化 SQL（仅返回优化后的建表语句，不生成 ER 图）
     */
    @PostMapping("/optimize-sql")
    public R<ErSqlOptimizeResponse> optimizeSql(@RequestBody @Valid ErSqlOptimizeRequest request) {
        return R.ok(erDiagramService.optimizeSql(request));
    }
}
