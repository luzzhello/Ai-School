package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 功能消费报价
 */
@Data
public class FeatureQuoteVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String featureCode;
    private String featureName;
    private String priceType;
    private Long priceCoins;
    private Long balance;
    private Long balanceAfter;
    private Boolean sufficient;
    private Integer wordCount;

    /** 是否适用会员每日配额 */
    private Boolean quotaApplicable;
    private String planCode;
    private String planName;
    /** 每日上限；-1 无限次 */
    private Integer dailyLimit;
    private Integer usedToday;
    private Integer quotaRemaining;
    private Boolean quotaUnlimited;
    /** 本次是否走会员免费额度 */
    private Boolean withinQuota;
    private Boolean quotaExceeded;
    /** 会员今日次数已用尽，按免费用户金币计费 */
    private Boolean coinFallback;
}
