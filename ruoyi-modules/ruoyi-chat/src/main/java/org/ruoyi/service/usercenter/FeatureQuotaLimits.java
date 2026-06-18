package org.ruoyi.service.usercenter;

/**
 * 会员每日功能次数工具类（实际限额由 uc_membership_feature_quota 表配置）
 */
public final class FeatureQuotaLimits {

    private FeatureQuotaLimits() {
    }

    public static boolean isUnlimited(Integer limit) {
        return limit != null && limit == -1;
    }
}
