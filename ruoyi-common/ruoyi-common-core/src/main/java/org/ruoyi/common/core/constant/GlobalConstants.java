package org.ruoyi.common.core.constant;

/**
 * 全局的key常量 (业务无关的key)
 *
 * @author Lion Li
 */
public interface GlobalConstants {

    /**
     * 全局 redis key (业务无关的key)
     */
    String GLOBAL_REDIS_KEY = "global:";

    /**
     * 验证码 redis key
     */
    String CAPTCHA_CODE_KEY = GLOBAL_REDIS_KEY + "captcha_codes:";

    /**
     * 防重提交 redis key
     */
    String REPEAT_SUBMIT_KEY = GLOBAL_REDIS_KEY + "repeat_submit:";

    /**
     * 限流 redis key
     */
    String RATE_LIMIT_KEY = GLOBAL_REDIS_KEY + "rate_limit:";

    /**
     * 三方认证 redis key
     */
    String SOCIAL_AUTH_CODE_KEY = GLOBAL_REDIS_KEY + "social_auth_codes:";

    /**
     * 微信公众号扫码登录 ticket redis key
     */
    String WX_QRCODE_TICKET_KEY = GLOBAL_REDIS_KEY + "wx_qrcode_ticket:";

    /**
     * 微信公众号 access_token redis key
     */
    String WX_MP_ACCESS_TOKEN_KEY = GLOBAL_REDIS_KEY + "wx_mp_access_token";
}
