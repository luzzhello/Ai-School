package org.ruoyi.service.usercenter.impl;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.usercenter.UcUserMembership;
import org.ruoyi.domain.entity.usercenter.UcWallet;
import org.ruoyi.domain.vo.usercenter.UserCenterOverviewVo;
import org.ruoyi.mapper.usercenter.UcWalletLogMapper;
import org.ruoyi.mapper.usercenter.UcWalletMapper;
import org.ruoyi.service.usercenter.IUserCenterService;
import org.ruoyi.service.usercenter.IUserMembershipService;
import org.ruoyi.service.usercenter.IUserWalletService;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.core.utils.InviteCodeUtils;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class UserCenterServiceImpl implements IUserCenterService {

    private final IUserWalletService walletService;
    private final IUserMembershipService membershipService;
    private final UcWalletMapper walletMapper;
    private final UcWalletLogMapper walletLogMapper;

    @Override
    public UserCenterOverviewVo sidebar(Long userId, String username, String nickName, String inviteCode) {
        return fillBasicInfo(userId, username, nickName, inviteCode);
    }

    @Override
    public UserCenterOverviewVo overview(Long userId, String username, String nickName, String inviteCode) {
        UserCenterOverviewVo vo = fillBasicInfo(userId, username, nickName, inviteCode);
        try {
            UcUserMembership membership = membershipService.getActiveMembership(userId);
            vo.setMembershipPlanCode(membership.getPlanCode());
            vo.setMembershipPlanName(membership.getPlanName());
            vo.setMembershipExpireTime(membership.getExpireTime());
        } catch (Exception ignored) {
            vo.setMembershipPlanCode(UserCenterConstants.PLAN_FREE);
            vo.setMembershipPlanName("免费会员");
        }
        return vo;
    }

    private UserCenterOverviewVo fillBasicInfo(Long userId, String username, String nickName, String inviteCode) {
        UserCenterOverviewVo vo = new UserCenterOverviewVo();
        vo.setUserId(userId);
        vo.setUsername(username);
        vo.setNickName(nickName);
        vo.setInviteCode(resolveInviteCode(userId, inviteCode));
        vo.setCoinBalance(walletService.getBalance(userId));
        Date joinTime = resolveJoinTime(userId);
        vo.setJoinTime(joinTime);
        vo.setUsedDays(calcUsedDays(joinTime));
        return vo;
    }

    private Date resolveJoinTime(Long userId) {
        return TenantHelper.ignore(() -> {
            UcWallet wallet = walletMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<UcWallet>lambdaQuery()
                    .eq(UcWallet::getUserId, userId)
                    .last("LIMIT 1"));
            if (wallet != null && wallet.getCreateTime() != null) {
                return wallet.getCreateTime();
            }
            Date firstLogTime = walletLogMapper.minCreateTimeByUserId(userId);
            if (firstLogTime != null) {
                return firstLogTime;
            }
            return new Date();
        });
    }

    private int calcUsedDays(Date joinTime) {
        if (joinTime == null) {
            return 0;
        }
        long diff = System.currentTimeMillis() - joinTime.getTime();
        return (int) Math.max(1, TimeUnit.MILLISECONDS.toDays(diff) + 1);
    }

    private String resolveInviteCode(Long userId, String inviteCode) {
        if (StringUtils.isNotBlank(inviteCode)) {
            return inviteCode;
        }
        return InviteCodeUtils.generateForUserId(userId);
    }
}
