package org.ruoyi.system.controller.usercenter;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.system.domain.vo.SysNoticeVo;
import org.ruoyi.system.service.ISysNoticeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统公告（C 端个人中心）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/usercenter/notice")
public class UserNoticeController extends BaseController {

    private final ISysNoticeService noticeService;

    @GetMapping("/page")
    public TableDataInfo<SysNoticeVo> page(
        @RequestParam(required = false) String noticeType,
        PageQuery pageQuery) {
        return noticeService.selectPublicPageList(noticeType, pageQuery);
    }

    @GetMapping("/latest")
    public R<SysNoticeVo> latest() {
        return R.ok(noticeService.selectLatestPublicNotice());
    }

    @GetMapping("/{noticeId}")
    public R<SysNoticeVo> getInfo(@PathVariable Long noticeId) {
        return R.ok(noticeService.selectPublicNoticeById(noticeId));
    }
}
