package org.ruoyi.service.usercenter;

/**
 * 个人中心常量
 */
public final class UserCenterConstants {

    private UserCenterConstants() {
    }

    /** 1 元人民币 = 100 金币 */
    public static final long COINS_PER_YUAN = 100L;

    public static final String PLAN_FREE = "FREE";

    public static final String PLAN_WEEK = "WEEK";

    public static final String PLAN_MONTH = "MONTH";

    public static final String PLAN_YEAR = "YEAR";

    /** 会员每日功能次数达上限 */
    public static final String MSG_QUOTA_EXCEEDED = "已达到最大使用限制";

    public static final String BIZ_RECHARGE = "RECHARGE";

    /** 支付订单：待支付 */
    public static final String PAY_ORDER_PENDING = "0";

    /** 支付订单：已支付 */
    public static final String PAY_ORDER_PAID = "1";

    /** 支付订单：已关闭 */
    public static final String PAY_ORDER_CLOSED = "2";

    /** 支付订单：已过期 */
    public static final String PAY_ORDER_EXPIRED = "3";

    public static final String ORDER_TYPE_RECHARGE = "RECHARGE";

    public static final String ORDER_TYPE_MEMBERSHIP = "MEMBERSHIP";

    /** 订单默认支付有效期（分钟） */
    public static final int PAY_ORDER_EXPIRE_MINUTES = 30;

    public static final String BIZ_MEMBERSHIP_BUY = "MEMBERSHIP_BUY";

    public static final String BIZ_MEMBERSHIP_REFUND = "MEMBERSHIP_REFUND";

    public static final String BIZ_CHECK_IN = "CHECK_IN";

    public static final String BIZ_INVITE = "INVITE";

    public static final String BIZ_INVITE_BIND = "INVITE_BIND";

    public static final String BIZ_REDEEM = "REDEEM";

    public static final String BIZ_ACTIVITY_REWARD = "ACTIVITY_REWARD";

    public static final String BIZ_FEATURE_CONSUME = "FEATURE_CONSUME";

    public static final String SUBMISSION_PENDING = "0";

    public static final String SUBMISSION_APPROVED = "1";

    public static final String SUBMISSION_REJECTED = "2";

    /** 每日签到基础奖励 */
    public static final long CHECK_IN_DAILY_COINS = 1L;

    /** 连续签到每满 7 天额外奖励 */
    public static final long CHECK_IN_STREAK_BONUS = 2L;

    /** 邀请双方各得金币 */
    public static final long INVITE_REWARD_COINS = 100L;

    /** 邀请人每月金币上限 */
    public static final long INVITE_MONTHLY_CAP = 500L;

    public static final String MEMBERSHIP_ACTIVE = "0";

    public static final String MEMBERSHIP_EXPIRED = "1";
}
