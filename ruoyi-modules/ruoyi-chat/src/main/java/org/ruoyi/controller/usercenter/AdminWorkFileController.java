package org.ruoyi.controller.usercenter;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.bo.usercenter.UcWorkFileBo;
import org.ruoyi.domain.vo.usercenter.UcWorkFileAdminVo;
import org.ruoyi.service.usercenter.IAdminWorkFileService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户云端文件（管理端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/ucWorkFile")
public class AdminWorkFileController extends BaseController {

    private final IAdminWorkFileService workFileService;

    @SaCheckPermission("system:ucWorkFile:list")
    @GetMapping("/list")
    public TableDataInfo<UcWorkFileAdminVo> list(UcWorkFileBo bo, PageQuery pageQuery) {
        return workFileService.queryPageList(bo, pageQuery);
    }
}
