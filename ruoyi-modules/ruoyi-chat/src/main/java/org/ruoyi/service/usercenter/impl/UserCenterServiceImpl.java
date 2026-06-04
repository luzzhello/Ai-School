package org.ruoyi.service.usercenter.impl;

import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.entity.usercenter.UcUserMembership;
import org.ruoyi.domain.entity.usercenter.UcWallet;
import org.ruoyi.domain.vo.usercenter.UserCenterOverviewVo;
import org.ruoyi.mapper.usercenter.UcWalletMapper;
import org.ruoyi.service.usercenter.IUserCenterService;
import org.ruoyi.service.usercenter.IUserMembershipService;
import org.ruoyi.service.usercenter.IUserWalletService;
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

    @Override
    public UserCenterOverviewVo overview(Long userId, String username, String nickName) {
        UserCenterOverviewVo vo = new UserCenterOverviewVo();
        vo.setUserId(userId);
        vo.setUsername(username);
        vo.setNickName(nickName);
        vo.setInviteCode(buildInviteCode(userId));
        vo.setCoinBalance(walletService.getBalance(userId));

        UcUserMembership membership = membershipService.getActiveMembership(userId);
        vo.setMembershipPlanCode(membership.getPlanCode());
        vo.setMembershipPlanName(membership.getPlanName());
        vo.setMembershipExpireTime(membership.getExpireTime());

        UcWallet wallet = walletMapper.selectOne(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<UcWallet>lambdaQuery()
                .eq(UcWallet::getUserId, userId)
                .last("LIMIT 1"));
        Date joinTime = wallet != null ? wallet.getCreateTime() : new Date();
        vo.setJoinTime(joinTime);
        vo.setUsedDays(calcUsedDays(joinTime));
        return vo;
    }

    private int calcUsedDays(Date joinTime) {
        if (joinTime == null) {
            return 0;
        }
        long diff = System.currentTimeMillis() - joinTime.getTime();
        return (int) Math.max(1, TimeUnit.MILLISECONDS.toDays(diff) + 1);
    }

    private String buildInviteCode(Long userId) {
        return Long.toHexString(userId == null ? 0L : userId).toUpperCase();
    }
}
