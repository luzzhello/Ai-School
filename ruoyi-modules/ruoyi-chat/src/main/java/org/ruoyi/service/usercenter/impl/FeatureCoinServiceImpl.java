package org.ruoyi.service.usercenter.impl;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.usercenter.UcFeaturePrice;
import org.ruoyi.domain.vo.usercenter.FeatureQuotaSnapshot;
import org.ruoyi.domain.vo.usercenter.FeatureQuoteVo;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.ruoyi.service.usercenter.IFeaturePriceService;
import org.ruoyi.service.usercenter.IFeatureQuotaService;
import org.ruoyi.service.usercenter.IUserWalletService;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class FeatureCoinServiceImpl implements IFeatureCoinService {

    private final IFeaturePriceService featurePriceService;
    private final IUserWalletService walletService;
    private final IFeatureQuotaService featureQuotaService;

    @Override
    public FeatureQuoteVo quote(Long userId, String featureCode, Integer wordCount) {
        UcFeaturePrice price = featurePriceService.requireEnabledByCode(featureCode);
        FeatureQuotaSnapshot quota = featureQuotaService.snapshot(userId, featureCode);
        long cost = resolveCost(userId, featureCode, wordCount, quota);
        long balance = walletService.getBalance(userId);
        FeatureQuoteVo vo = new FeatureQuoteVo();
        vo.setFeatureCode(featureCode);
        vo.setFeatureName(price.getFeatureName());
        vo.setPriceType(price.getPriceType());
        vo.setPriceCoins(cost);
        vo.setBalance(balance);
        vo.setBalanceAfter(balance - cost);
        vo.setSufficient(cost <= 0 || balance >= cost);
        vo.setWordCount(wordCount);
        fillQuota(vo, quota);
        return vo;
    }

    @Override
    public void requireAffordable(Long userId, String featureCode, Integer wordCount) {
        FeatureQuotaSnapshot quota = featureQuotaService.snapshot(userId, featureCode);
        long cost = resolveCost(userId, featureCode, wordCount, quota);
        if (cost <= 0) {
            return;
        }
        long balance = walletService.getBalance(userId);
        if (balance < cost) {
            throw new ServiceException("金币余额不足，需要 " + cost + " 金币，当前余额 " + balance);
        }
    }

    @Override
    public void requireAffordableForLoginUser(String featureCode, Integer wordCount) {
        if (!LoginHelper.isLogin()) {
            throw new ServiceException("请先登录");
        }
        requireAffordable(LoginHelper.getUserId(), featureCode, wordCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long charge(Long userId, String featureCode, Integer wordCount) {
        FeatureQuotaSnapshot quota = featureQuotaService.snapshot(userId, featureCode);
        if (shouldUseMemberQuota(quota)) {
            featureQuotaService.consumeIfApplicable(userId, featureCode);
            return 0L;
        }
        UcFeaturePrice price = featurePriceService.requireEnabledByCode(featureCode);
        long cost = calcCost(price, wordCount);
        if (cost <= 0) {
            return 0L;
        }
        long balance = walletService.getBalance(userId);
        if (balance < cost) {
            throw new ServiceException("金币余额不足，需要 " + cost + " 金币，当前余额 " + balance);
        }
        String bizNo = "FC" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        walletService.changeBalance(userId, -cost, UserCenterConstants.BIZ_FEATURE_CONSUME, bizNo,
            price.getFeatureName() + " 消费 " + cost + " 金币");
        return cost;
    }

    @Override
    public long chargeForLoginUser(String featureCode, Integer wordCount) {
        if (!LoginHelper.isLogin()) {
            throw new ServiceException("请先登录");
        }
        return charge(LoginHelper.getUserId(), featureCode, wordCount);
    }

    private long resolveCost(Long userId, String featureCode, Integer wordCount, FeatureQuotaSnapshot quota) {
        if (shouldUseMemberQuota(quota)) {
            return 0L;
        }
        UcFeaturePrice price = featurePriceService.requireEnabledByCode(featureCode);
        return calcCost(price, wordCount);
    }

    private boolean shouldUseMemberQuota(FeatureQuotaSnapshot quota) {
        return Boolean.TRUE.equals(quota.getQuotaApplicable())
            && (Boolean.TRUE.equals(quota.getQuotaUnlimited()) || Boolean.TRUE.equals(quota.getWithinQuota()));
    }

    private void fillQuota(FeatureQuoteVo vo, FeatureQuotaSnapshot quota) {
        vo.setQuotaApplicable(quota.getQuotaApplicable());
        vo.setPlanCode(quota.getPlanCode());
        vo.setPlanName(quota.getPlanName());
        vo.setDailyLimit(quota.getDailyLimit());
        vo.setUsedToday(quota.getUsedToday());
        vo.setQuotaRemaining(quota.getQuotaRemaining());
        vo.setQuotaUnlimited(quota.getQuotaUnlimited());
        vo.setWithinQuota(quota.getWithinQuota());
        vo.setQuotaExceeded(quota.getQuotaExceeded());
        vo.setCoinFallback(Boolean.TRUE.equals(quota.getQuotaApplicable())
            && Boolean.TRUE.equals(quota.getQuotaExceeded()));
    }

    private long calcCost(UcFeaturePrice price, Integer wordCount) {
        long unit = price.getPriceCoins() == null ? 0L : price.getPriceCoins();
        if (unit <= 0) {
            return 0L;
        }
        if (FeatureCodes.PRICE_TYPE_PER_THOUSAND.equals(price.getPriceType())) {
            int words = wordCount == null ? 0 : wordCount;
            if (words <= 0) {
                return unit;
            }
            return Math.max(1L, (long) Math.ceil(words / 1000.0) * unit);
        }
        return unit;
    }
}
