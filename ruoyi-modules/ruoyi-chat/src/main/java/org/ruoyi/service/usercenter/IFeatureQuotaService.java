package org.ruoyi.service.usercenter;

import org.ruoyi.domain.vo.usercenter.FeatureQuotaSnapshot;

public interface IFeatureQuotaService {

    /**
     * 查询用户某功能今日配额状态
     */
    FeatureQuotaSnapshot snapshot(Long userId, String featureCode);

    /**
     * 使用前校验：超限则抛出「已达到最大使用限制」
     */
    void requireAvailable(Long userId, String featureCode);

    /**
     * 成功使用后扣减会员免费次数（无限次或无配额时不记录）
     */
    void consumeIfApplicable(Long userId, String featureCode);
}
