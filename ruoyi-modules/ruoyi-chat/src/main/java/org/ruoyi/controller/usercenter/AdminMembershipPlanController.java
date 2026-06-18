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
import org.ruoyi.domain.bo.usercenter.UcMembershipPlanBo;
import org.ruoyi.domain.vo.usercenter.UcMembershipPlanVo;
import org.ruoyi.service.usercenter.IMembershipPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员套餐配置（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/membershipPlan")
public class AdminMembershipPlanController extends BaseController {

    private final IMembershipPlanService membershipPlanService;

    @SaCheckPermission("system:membershipPlan:list")
    @GetMapping("/list")
    public TableDataInfo<UcMembershipPlanVo> list(UcMembershipPlanBo bo, PageQuery pageQuery) {
        return membershipPlanService.queryPageList(bo, pageQuery);
    }

    @SaCheckPermission("system:membershipPlan:query")
    @GetMapping("/{planId}")
    public R<UcMembershipPlanVo> getInfo(@NotNull @PathVariable Long planId) {
        return R.ok(membershipPlanService.queryById(planId));
    }

    @SaCheckPermission("system:membershipPlan:add")
    @Log(title = "会员配置", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody UcMembershipPlanBo bo) {
        return toAjax(membershipPlanService.insertByBo(bo));
    }

    @SaCheckPermission("system:membershipPlan:edit")
    @Log(title = "会员配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody UcMembershipPlanBo bo) {
        return toAjax(membershipPlanService.updateByBo(bo));
    }

    @SaCheckPermission("system:membershipPlan:remove")
    @Log(title = "会员配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] planIds) {
        return toAjax(membershipPlanService.deleteWithValidByIds(List.of(planIds), true));
    }
}
