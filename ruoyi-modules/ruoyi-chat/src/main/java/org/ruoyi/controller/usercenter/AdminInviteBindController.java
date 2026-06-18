package org.ruoyi.controller.usercenter;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.bo.usercenter.UcInviteBindBo;
import org.ruoyi.domain.vo.usercenter.UcInviteBindVo;
import org.ruoyi.service.usercenter.IAdminInviteBindService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户邀请记录（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/ucInvite")
public class AdminInviteBindController extends BaseController {

    private final IAdminInviteBindService inviteBindService;

    @SaCheckPermission("system:ucInvite:list")
    @GetMapping("/list")
    public TableDataInfo<UcInviteBindVo> list(UcInviteBindBo bo, PageQuery pageQuery) {
        return inviteBindService.queryPageList(bo, pageQuery);
    }
}
