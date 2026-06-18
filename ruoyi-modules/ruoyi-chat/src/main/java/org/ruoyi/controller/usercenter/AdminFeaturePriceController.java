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
import org.ruoyi.domain.bo.usercenter.UcFeaturePriceBo;
import org.ruoyi.domain.vo.usercenter.UcFeaturePriceVo;
import org.ruoyi.service.usercenter.IFeaturePriceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 功能定价（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/featurePrice")
public class AdminFeaturePriceController extends BaseController {

    private final IFeaturePriceService featurePriceService;

    @SaCheckPermission("system:featurePrice:list")
    @GetMapping("/list")
    public TableDataInfo<UcFeaturePriceVo> list(UcFeaturePriceBo bo, PageQuery pageQuery) {
        return featurePriceService.queryPageList(bo, pageQuery);
    }

    @SaCheckPermission("system:featurePrice:query")
    @GetMapping("/{id}")
    public R<UcFeaturePriceVo> getInfo(@NotNull @PathVariable Long id) {
        return R.ok(featurePriceService.queryById(id));
    }

    @SaCheckPermission("system:featurePrice:add")
    @Log(title = "功能定价", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody UcFeaturePriceBo bo) {
        return toAjax(featurePriceService.insertByBo(bo));
    }

    @SaCheckPermission("system:featurePrice:edit")
    @Log(title = "功能定价", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody UcFeaturePriceBo bo) {
        return toAjax(featurePriceService.updateByBo(bo));
    }

    @SaCheckPermission("system:featurePrice:remove")
    @Log(title = "功能定价", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] ids) {
        return toAjax(featurePriceService.deleteWithValidByIds(List.of(ids), true));
    }
}
