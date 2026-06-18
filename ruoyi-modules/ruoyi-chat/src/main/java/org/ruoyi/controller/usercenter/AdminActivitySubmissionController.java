package org.ruoyi.controller.usercenter;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.bo.usercenter.UcActivitySubmissionAuditBo;
import org.ruoyi.domain.bo.usercenter.UcActivitySubmissionBo;
import org.ruoyi.domain.vo.usercenter.UcActivitySubmissionVo;
import org.ruoyi.service.usercenter.IAdminActivitySubmissionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户分享/Bug 反馈审核（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/ucSubmission")
public class AdminActivitySubmissionController extends BaseController {

    private final IAdminActivitySubmissionService submissionService;

    @SaCheckPermission("system:ucShare:list")
    @GetMapping("/share/list")
    public TableDataInfo<UcActivitySubmissionVo> shareList(UcActivitySubmissionBo bo, PageQuery pageQuery) {
        bo.setActivityType("SHARE");
        return submissionService.queryPageList(bo, pageQuery);
    }

    @SaCheckPermission("system:ucBug:list")
    @GetMapping("/bug/list")
    public TableDataInfo<UcActivitySubmissionVo> bugList(UcActivitySubmissionBo bo, PageQuery pageQuery) {
        bo.setActivityType("BUG");
        return submissionService.queryPageList(bo, pageQuery);
    }

    @SaCheckPermission(value = {"system:ucShare:query", "system:ucBug:query"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @GetMapping("/{id}")
    public R<UcActivitySubmissionVo> getInfo(@PathVariable Long id) {
        return R.ok(submissionService.queryById(id));
    }

    @SaCheckPermission(value = {"system:ucShare:audit", "system:ucBug:audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @Log(title = "活动反馈审核", businessType = BusinessType.UPDATE)
    @PutMapping("/audit")
    public R<Void> audit(@RequestBody @Valid UcActivitySubmissionAuditBo bo) {
        submissionService.audit(bo);
        return R.ok();
    }
}
