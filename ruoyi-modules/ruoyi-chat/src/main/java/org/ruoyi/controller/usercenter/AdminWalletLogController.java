package org.ruoyi.controller.usercenter;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.bo.usercenter.UcWalletLogBo;
import org.ruoyi.domain.vo.usercenter.UcWalletLogAdminVo;
import org.ruoyi.service.usercenter.IAdminWalletLogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户金币流水（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/ucWalletLog")
public class AdminWalletLogController extends BaseController {

    private final IAdminWalletLogService walletLogService;

    @SaCheckPermission("system:ucWalletLog:list")
    @GetMapping("/list")
    public TableDataInfo<UcWalletLogAdminVo> list(UcWalletLogBo bo, PageQuery pageQuery) {
        return walletLogService.queryPageList(bo, pageQuery);
    }
}
