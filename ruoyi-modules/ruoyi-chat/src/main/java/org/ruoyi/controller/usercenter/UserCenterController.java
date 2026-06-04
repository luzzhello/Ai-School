package org.ruoyi.controller.usercenter;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.domain.model.LoginUser;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.dto.request.usercenter.MembershipPurchaseRequest;
import org.ruoyi.domain.dto.request.usercenter.WalletRechargeRequest;
import org.ruoyi.domain.dto.request.usercenter.WorkFileQueryRequest;
import org.ruoyi.domain.dto.request.usercenter.WorkFileSaveRequest;
import org.ruoyi.domain.entity.usercenter.UcUserMembership;
import org.ruoyi.domain.entity.usercenter.UcWorkFile;
import org.ruoyi.domain.vo.usercenter.UcMembershipPlanVo;
import org.ruoyi.domain.vo.usercenter.UcWalletLogVo;
import org.ruoyi.domain.vo.usercenter.UcWorkFileVo;
import org.ruoyi.domain.vo.usercenter.UserCenterOverviewVo;
import org.ruoyi.mapper.usercenter.UcWorkFileMapper;
import org.ruoyi.service.usercenter.IUserCenterService;
import org.ruoyi.service.usercenter.IUserMembershipService;
import org.ruoyi.service.usercenter.IUserWalletService;
import org.ruoyi.service.usercenter.IUserWorkFileService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人中心（C 端）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/usercenter")
public class UserCenterController extends BaseController {

    private final IUserCenterService userCenterService;
    private final IUserWalletService walletService;
    private final IUserMembershipService membershipService;
    private final IUserWorkFileService workFileService;
    private final UcWorkFileMapper workFileMapper;

    @GetMapping("/overview")
    public R<UserCenterOverviewVo> overview() {
        LoginUser user = requireLogin();
        return R.ok(userCenterService.overview(user.getUserId(), user.getUsername(), user.getNickname()));
    }

    @GetMapping("/wallet/balance")
    public R<Map<String, Long>> walletBalance() {
        Long userId = requireLogin().getUserId();
        Map<String, Long> data = new HashMap<>(2);
        data.put("balance", walletService.getBalance(userId));
        return R.ok(data);
    }

    @PostMapping("/wallet/recharge")
    public R<Map<String, Long>> recharge(@RequestBody @Valid WalletRechargeRequest request) {
        Long userId = requireLogin().getUserId();
        long coins = walletService.recharge(userId, request.getAmountYuan());
        Map<String, Long> data = new HashMap<>(2);
        data.put("addedCoins", coins);
        data.put("balance", walletService.getBalance(userId));
        return R.ok(data);
    }

    @GetMapping("/wallet/logs")
    public TableDataInfo<UcWalletLogVo> walletLogs(PageQuery pageQuery) {
        Long userId = requireLogin().getUserId();
        return walletService.listLogs(userId, pageQuery);
    }

    @GetMapping("/membership/current")
    public R<UcUserMembership> currentMembership() {
        Long userId = requireLogin().getUserId();
        return R.ok(membershipService.getActiveMembership(userId));
    }

    @GetMapping("/membership/plans")
    public R<List<UcMembershipPlanVo>> membershipPlans() {
        Long userId = requireLogin().getUserId();
        return R.ok(membershipService.listPlans(userId));
    }

    @PostMapping("/membership/purchase")
    public R<Void> purchaseMembership(@RequestBody @Valid MembershipPurchaseRequest request) {
        Long userId = requireLogin().getUserId();
        membershipService.purchase(userId, request.getPlanCode());
        return R.ok();
    }

    @GetMapping("/files/page")
    public TableDataInfo<UcWorkFileVo> filesPage(WorkFileQueryRequest query, PageQuery pageQuery) {
        Long userId = requireLogin().getUserId();
        return workFileService.page(userId, query, pageQuery);
    }

    @GetMapping("/files/{fileId}")
    public R<UcWorkFile> fileDetail(@PathVariable Long fileId) {
        Long userId = requireLogin().getUserId();
        UcWorkFile file = workFileMapper.selectById(fileId);
        if (file == null || !userId.equals(file.getUserId())) {
            throw new ServiceException("文件不存在或无权访问");
        }
        return R.ok(file);
    }

    @PostMapping("/files")
    public R<Long> saveFile(@RequestBody @Valid WorkFileSaveRequest request) {
        Long userId = requireLogin().getUserId();
        return R.ok(workFileService.save(userId, request));
    }

    @DeleteMapping("/files/{fileId}")
    public R<Void> deleteFile(@PathVariable Long fileId) {
        Long userId = requireLogin().getUserId();
        workFileService.remove(userId, fileId);
        return R.ok();
    }

    private LoginUser requireLogin() {
        if (!LoginHelper.isLogin()) {
            throw new ServiceException("请先登录");
        }
        return LoginHelper.getLoginUser();
    }
}
