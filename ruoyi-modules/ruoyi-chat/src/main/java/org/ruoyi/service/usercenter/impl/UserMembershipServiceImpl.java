package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.entity.usercenter.UcMembershipPlan;
import org.ruoyi.domain.entity.usercenter.UcUserMembership;
import org.ruoyi.domain.vo.usercenter.MembershipPurchaseCreateVo;
import org.ruoyi.domain.vo.usercenter.MembershipPurchasePreviewVo;
import org.ruoyi.domain.vo.usercenter.UcMembershipPlanVo;
import org.ruoyi.mapper.usercenter.UcMembershipPlanMapper;
import org.ruoyi.mapper.usercenter.UcUserMembershipMapper;
import org.ruoyi.service.usercenter.IPayOrderService;
import org.ruoyi.service.usercenter.IUserMembershipService;
import org.ruoyi.service.usercenter.IUserWalletService;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UserMembershipServiceImpl implements IUserMembershipService {

    private final UcMembershipPlanMapper planMapper;
    private final UcUserMembershipMapper membershipMapper;
    private final IUserWalletService walletService;
    private final ObjectProvider<IPayOrderService> payOrderServiceProvider;

    @Override
    public UcUserMembership getActiveMembership(Long userId) {
        refreshExpired(userId);
        LambdaQueryWrapper<UcUserMembership> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcUserMembership::getUserId, userId);
        lqw.eq(UcUserMembership::getStatus, UserCenterConstants.MEMBERSHIP_ACTIVE);
        lqw.orderByDesc(UcUserMembership::getExpireTime);
        List<UcUserMembership> actives = membershipMapper.selectList(lqw);
        UcUserMembership paid = actives.stream()
            .filter(m -> !UserCenterConstants.PLAN_FREE.equals(m.getPlanCode()))
            .findFirst()
            .orElse(null);
        if (paid != null) {
            return paid;
        }
        UcUserMembership free = actives.stream()
            .filter(m -> UserCenterConstants.PLAN_FREE.equals(m.getPlanCode()))
            .findFirst()
            .orElse(null);
        if (free != null) {
            return free;
        }
        return ensureFreeMembership(userId);
    }

    @Override
    public List<UcMembershipPlanVo> listPlans(Long userId) {
        UcUserMembership active = getActiveMembership(userId);
        String activeCode = active.getPlanCode();
        LambdaQueryWrapper<UcMembershipPlan> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcMembershipPlan::getStatus, "0");
        lqw.orderByAsc(UcMembershipPlan::getSortOrder);
        List<UcMembershipPlanVo> plans = planMapper.selectVoList(lqw);
        for (UcMembershipPlanVo plan : plans) {
            plan.setCurrent(StringUtils.equals(plan.getPlanCode(), activeCode));
        }
        return plans;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purchase(Long userId, String planCode) {
        createPurchaseOrder(userId, planCode);
    }

    @Override
    public MembershipPurchasePreviewVo previewPurchase(Long userId, String planCode) {
        UcMembershipPlan plan = getPlan(planCode);
        long priceCoins = plan.getPriceCoins();
        long balance = walletService.getBalance(userId);
        long coinsUsed = Math.min(balance, priceCoins);
        long shortageCoins = priceCoins - coinsUsed;
        BigDecimal cashYuan = BigDecimal.valueOf(shortageCoins)
            .divide(BigDecimal.valueOf(UserCenterConstants.COINS_PER_YUAN), 2, RoundingMode.HALF_UP);

        MembershipPurchasePreviewVo vo = new MembershipPurchasePreviewVo();
        vo.setPlanCode(planCode);
        vo.setPlanName(plan.getPlanName());
        vo.setPriceCoins(priceCoins);
        vo.setBalance(balance);
        vo.setCoinsUsed(coinsUsed);
        vo.setShortageCoins(shortageCoins);
        vo.setCashYuan(cashYuan);
        vo.setSufficient(shortageCoins == 0);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MembershipPurchaseCreateVo createPurchaseOrder(Long userId, String planCode) {
        if (UserCenterConstants.PLAN_FREE.equals(planCode)) {
            throw new ServiceException("免费会员无需购买");
        }
        UcMembershipPlan plan = getPlan(planCode);
        MembershipPurchasePreviewVo preview = previewPurchase(userId, planCode);
        String orderNo = "MBR" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        if (preview.getCoinsUsed() > 0) {
            walletService.changeBalance(
                userId,
                -preview.getCoinsUsed(),
                UserCenterConstants.BIZ_MEMBERSHIP_BUY,
                orderNo,
                "购买「" + plan.getPlanName() + "」抵扣金币 " + preview.getCoinsUsed()
            );
        }

        MembershipPurchaseCreateVo vo = new MembershipPurchaseCreateVo();
        vo.setPlanCode(planCode);
        vo.setPlanName(plan.getPlanName());
        vo.setCoinsUsed(preview.getCoinsUsed());

        if (preview.getShortageCoins() == 0) {
            activateByPlanCode(userId, planCode, orderNo);
            payOrderService().createPaidMembershipOrder(
                userId, planCode, plan.getPlanName(), preview.getPriceCoins(), preview.getCoinsUsed(), orderNo);
            vo.setCompleted(true);
            vo.setOrderNo(orderNo);
            vo.setCashYuan(BigDecimal.ZERO);
            vo.setStatus(UserCenterConstants.PAY_ORDER_PAID);
            return vo;
        }

        payOrderService().createMembershipPendingOrder(
            orderNo,
            userId,
            planCode,
            plan.getPlanName(),
            preview.getPriceCoins(),
            preview.getCoinsUsed(),
            preview.getCashYuan()
        );
        vo.setCompleted(false);
        vo.setOrderNo(orderNo);
        vo.setCashYuan(preview.getCashYuan());
        vo.setStatus(UserCenterConstants.PAY_ORDER_PENDING);
        return vo;
    }

    private IPayOrderService payOrderService() {
        return payOrderServiceProvider.getObject();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateByPlanCode(Long userId, String planCode, String bizNo) {
        if (UserCenterConstants.PLAN_FREE.equals(planCode)) {
            throw new ServiceException("免费会员无需购买");
        }
        UcMembershipPlan plan = getPlan(planCode);
        Date now = new Date();
        UcUserMembership active = getActiveMembership(userId);
        Date start = now;
        Date expire;
        if (active.getExpireTime() != null
            && active.getExpireTime().after(now)
            && !UserCenterConstants.PLAN_FREE.equals(active.getPlanCode())) {
            start = active.getStartTime();
            expire = addDays(active.getExpireTime(), plan.getDurationDays());
        }
        else {
            expire = addDays(now, plan.getDurationDays());
        }
        expireAllActive(userId);

        UcUserMembership record = new UcUserMembership();
        record.setUserId(userId);
        record.setPlanCode(plan.getPlanCode());
        record.setPlanName(plan.getPlanName());
        record.setStartTime(start);
        record.setExpireTime(expire);
        record.setStatus(UserCenterConstants.MEMBERSHIP_ACTIVE);
        membershipMapper.insert(record);
    }

    private UcMembershipPlan getPlan(String planCode) {
        LambdaQueryWrapper<UcMembershipPlan> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcMembershipPlan::getPlanCode, planCode);
        lqw.eq(UcMembershipPlan::getStatus, "0");
        UcMembershipPlan plan = planMapper.selectOne(lqw);
        if (plan == null) {
            throw new ServiceException("会员套餐不存在");
        }
        if (plan.getPriceCoins() == null || plan.getPriceCoins() <= 0) {
            throw new ServiceException("套餐价格配置异常");
        }
        return plan;
    }

    private UcUserMembership ensureFreeMembership(Long userId) {
        UcUserMembership free = new UcUserMembership();
        free.setUserId(userId);
        free.setPlanCode(UserCenterConstants.PLAN_FREE);
        free.setPlanName("免费会员");
        free.setStartTime(new Date());
        free.setExpireTime(null);
        free.setStatus(UserCenterConstants.MEMBERSHIP_ACTIVE);
        membershipMapper.insert(free);
        return free;
    }

    private void expireAllActive(Long userId) {
        LambdaQueryWrapper<UcUserMembership> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcUserMembership::getUserId, userId);
        lqw.eq(UcUserMembership::getStatus, UserCenterConstants.MEMBERSHIP_ACTIVE);
        for (UcUserMembership item : membershipMapper.selectList(lqw)) {
            item.setStatus(UserCenterConstants.MEMBERSHIP_EXPIRED);
            membershipMapper.updateById(item);
        }
    }

    private void refreshExpired(Long userId) {
        LambdaQueryWrapper<UcUserMembership> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcUserMembership::getUserId, userId);
        lqw.eq(UcUserMembership::getStatus, UserCenterConstants.MEMBERSHIP_ACTIVE);
        lqw.isNotNull(UcUserMembership::getExpireTime);
        lqw.lt(UcUserMembership::getExpireTime, new Date());
        List<UcUserMembership> expiredList = membershipMapper.selectList(lqw);
        for (UcUserMembership item : expiredList) {
            item.setStatus(UserCenterConstants.MEMBERSHIP_EXPIRED);
            membershipMapper.updateById(item);
        }
    }

    private Date addDays(Date base, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(base);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }
}
