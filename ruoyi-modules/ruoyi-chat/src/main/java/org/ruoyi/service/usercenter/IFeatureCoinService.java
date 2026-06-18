package org.ruoyi.service.usercenter;

import org.ruoyi.domain.vo.usercenter.FeatureQuoteVo;

public interface IFeatureCoinService {

    FeatureQuoteVo quote(Long userId, String featureCode, Integer wordCount);

    /** 校验余额是否足够，不扣费 */
    void requireAffordable(Long userId, String featureCode, Integer wordCount);

    void requireAffordableForLoginUser(String featureCode, Integer wordCount);

    /** 扣费并返回实际扣除金币数（成功完成后调用） */
    long charge(Long userId, String featureCode, Integer wordCount);

    long chargeForLoginUser(String featureCode, Integer wordCount);
}
