package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员功能每日配额快照
 */
@Data
public class FeatureQuotaSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否适用会员每日配额 */
    private Boolean quotaApplicable;

    /** 当前会员编码 */
    private String planCode;

    /** 当前会员名称 */
    private String planName;

    /** 每日上限；-1 无限次 */
    private Integer dailyLimit;

    /** 今日已用 */
    private Integer usedToday;

    /** 今日剩余；-1 表示无限 */
    private Integer quotaRemaining;

    /** 是否无限次会员权益 */
    private Boolean quotaUnlimited;

    /** 本次是否走会员免费额度 */
    private Boolean withinQuota;

    /** 今日次数是否已用尽 */
    private Boolean quotaExceeded;
}
