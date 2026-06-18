package org.ruoyi.controller.usercenter;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.bo.usercenter.UcPayOrderBo;
import org.ruoyi.domain.vo.usercenter.UcPayOrderAdminVo;
import org.ruoyi.service.usercenter.IAdminPayOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户支付订单（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/ucPayOrder")
public class AdminPayOrderController extends BaseController {

    private final IAdminPayOrderService payOrderService;

    @SaCheckPermission("system:ucPayOrder:list")
    @GetMapping("/list")
    public TableDataInfo<UcPayOrderAdminVo> list(UcPayOrderBo bo, PageQuery pageQuery) {
        return payOrderService.queryPageList(bo, pageQuery);
    }
}
