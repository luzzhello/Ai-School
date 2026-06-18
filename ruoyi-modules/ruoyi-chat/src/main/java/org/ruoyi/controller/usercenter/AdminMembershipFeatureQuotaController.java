package org.ruoyi.controller.usercenter;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.idempotent.annotation.RepeatSubmit;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.bo.usercenter.UcMembershipFeatureQuotaBo;
import org.ruoyi.domain.vo.usercenter.UcMembershipFeatureQuotaVo;
import org.ruoyi.service.usercenter.IMembershipFeatureQuotaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员功能费用与次数配额（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/membershipFeatureQuota")
public class AdminMembershipFeatureQuotaController extends BaseController {

    private final IMembershipFeatureQuotaService membershipFeatureQuotaService;

    @SaCheckPermission("system:membershipFeatureQuota:list")
    @GetMapping("/list")
    public TableDataInfo<UcMembershipFeatureQuotaVo> list(UcMembershipFeatureQuotaBo bo, PageQuery pageQuery) {
        return membershipFeatureQuotaService.queryPageList(bo, pageQuery);
    }

    @SaCheckPermission("system:membershipFeatureQuota:query")
    @GetMapping("/{quotaId}")
    public R<UcMembershipFeatureQuotaVo> getInfo(@NotNull @PathVariable Long quotaId) {
        return R.ok(membershipFeatureQuotaService.queryById(quotaId));
    }

    @SaCheckPermission("system:membershipFeatureQuota:add")
    @Log(title = "会员功能配额", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody UcMembershipFeatureQuotaBo bo) {
        return toAjax(membershipFeatureQuotaService.insertByBo(bo));
    }

    @SaCheckPermission("system:membershipFeatureQuota:edit")
    @Log(title = "会员功能配额", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody UcMembershipFeatureQuotaBo bo) {
        return toAjax(membershipFeatureQuotaService.updateByBo(bo));
    }

    @SaCheckPermission("system:membershipFeatureQuota:remove")
    @Log(title = "会员功能配额", businessType = BusinessType.DELETE)
    @DeleteMapping("/{quotaIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] quotaIds) {
        return toAjax(membershipFeatureQuotaService.deleteWithValidByIds(List.of(quotaIds), true));
    }
}
