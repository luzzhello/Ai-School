package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.entity.usercenter.UcFeatureDailyUsage;
import org.ruoyi.domain.entity.usercenter.UcUserMembership;
import org.ruoyi.domain.vo.usercenter.FeatureQuotaSnapshot;
import org.ruoyi.mapper.usercenter.UcFeatureDailyUsageMapper;
import org.ruoyi.service.usercenter.IFeatureQuotaService;
import org.ruoyi.service.usercenter.IMembershipFeatureQuotaService;
import org.ruoyi.service.usercenter.IUserMembershipService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;

@RequiredArgsConstructor
@Service
public class FeatureQuotaServiceImpl implements IFeatureQuotaService {

    private final IUserMembershipService membershipService;
    private final IMembershipFeatureQuotaService membershipFeatureQuotaService;
    private final UcFeatureDailyUsageMapper usageMapper;

    @Override
    public FeatureQuotaSnapshot snapshot(Long userId, String featureCode) {
        UcUserMembership membership = membershipService.getActiveMembership(userId);
        String planCode = membership.getPlanCode();
        Integer dailyLimit = membershipFeatureQuotaService.resolveLimit(planCode, featureCode);

        FeatureQuotaSnapshot snap = new FeatureQuotaSnapshot();
        snap.setPlanCode(planCode);
        snap.setPlanName(membership.getPlanName());

        if (dailyLimit == null) {
            snap.setQuotaApplicable(false);
            snap.setQuotaUnlimited(false);
            snap.setWithinQuota(false);
            snap.setQuotaExceeded(false);
            snap.setDailyLimit(null);
            snap.setUsedToday(0);
            snap.setQuotaRemaining(null);
            return snap;
        }

        snap.setQuotaApplicable(true);
        if (membershipFeatureQuotaService.isUnlimited(dailyLimit)) {
            snap.setDailyLimit(-1);
            snap.setUsedToday(0);
            snap.setQuotaRemaining(-1);
            snap.setQuotaUnlimited(true);
            snap.setWithinQuota(true);
            snap.setQuotaExceeded(false);
            return snap;
        }

        int used = getUsedToday(userId, featureCode);
        int remaining = Math.max(0, dailyLimit - used);
        snap.setDailyLimit(dailyLimit);
        snap.setUsedToday(used);
        snap.setQuotaRemaining(remaining);
        snap.setQuotaUnlimited(false);
        snap.setWithinQuota(remaining > 0);
        snap.setQuotaExceeded(remaining == 0);
        return snap;
    }

    @Override
    public void requireAvailable(Long userId, String featureCode) {
        // 会员次数用尽后允许按免费用户金币计费，此处不做拦截
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeIfApplicable(Long userId, String featureCode) {
        FeatureQuotaSnapshot snap = snapshot(userId, featureCode);
        if (!Boolean.TRUE.equals(snap.getQuotaApplicable())) {
            return;
        }
        if (Boolean.TRUE.equals(snap.getQuotaUnlimited())) {
            return;
        }
        if (!Boolean.TRUE.equals(snap.getWithinQuota())) {
            return;
        }
        incrementUsage(userId, featureCode, LocalDate.now());
    }

    private int getUsedToday(Long userId, String featureCode) {
        Integer count = usageMapper.selectUseCount(userId, featureCode, LocalDate.now());
        return count == null ? 0 : count;
    }

    private void incrementUsage(Long userId, String featureCode, LocalDate usageDate) {
        LambdaQueryWrapper<UcFeatureDailyUsage> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcFeatureDailyUsage::getUserId, userId);
        lqw.eq(UcFeatureDailyUsage::getFeatureCode, featureCode);
        lqw.eq(UcFeatureDailyUsage::getUsageDate, usageDate);
        UcFeatureDailyUsage existing = usageMapper.selectOne(lqw);
        Date now = new Date();
        if (existing == null) {
            UcFeatureDailyUsage row = new UcFeatureDailyUsage();
            row.setUserId(userId);
            row.setFeatureCode(featureCode);
            row.setUsageDate(usageDate);
            row.setUseCount(1);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            try {
                usageMapper.insert(row);
            }
            catch (DuplicateKeyException ex) {
                bumpExisting(userId, featureCode, usageDate, now);
            }
            return;
        }
        existing.setUseCount((existing.getUseCount() == null ? 0 : existing.getUseCount()) + 1);
        existing.setUpdateTime(now);
        usageMapper.updateById(existing);
    }

    private void bumpExisting(Long userId, String featureCode, LocalDate usageDate, Date now) {
        LambdaQueryWrapper<UcFeatureDailyUsage> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcFeatureDailyUsage::getUserId, userId);
        lqw.eq(UcFeatureDailyUsage::getFeatureCode, featureCode);
        lqw.eq(UcFeatureDailyUsage::getUsageDate, usageDate);
        UcFeatureDailyUsage existing = usageMapper.selectOne(lqw);
        if (existing == null) {
            return;
        }
        existing.setUseCount((existing.getUseCount() == null ? 0 : existing.getUseCount()) + 1);
        existing.setUpdateTime(now);
        usageMapper.updateById(existing);
    }
}
