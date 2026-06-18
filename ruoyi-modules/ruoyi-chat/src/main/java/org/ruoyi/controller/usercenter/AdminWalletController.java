package org.ruoyi.controller.usercenter;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.bo.usercenter.UcWalletBo;
import org.ruoyi.domain.vo.usercenter.UcWalletAdminVo;
import org.ruoyi.service.usercenter.IAdminWalletService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户钱包（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/ucWallet")
public class AdminWalletController extends BaseController {

    private final IAdminWalletService walletService;

    @SaCheckPermission("system:ucWallet:list")
    @GetMapping("/list")
    public TableDataInfo<UcWalletAdminVo> list(UcWalletBo bo, PageQuery pageQuery) {
        return walletService.queryPageList(bo, pageQuery);
    }
}
