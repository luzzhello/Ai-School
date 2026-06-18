package org.ruoyi.system.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信公众号（扫码登录）配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.mp")
public class WxMpProperties {

    /**
     * 是否启用微信扫码登录
     */
    private boolean enabled = true;

    /**
     * 微信公众平台服务器配置 Token（用于签名校验）
     */
    private String token = "";

    /**
     * 公众号 AppId，留空则回退读取 justauth.type.wechat_mp.client-id
     */
    private String appId = "";

    /**
     * 公众号 AppSecret，留空则回退读取 justauth.type.wechat_mp.client-secret
     */
    private String appSecret = "";

    /**
     * 二维码有效期（秒）
     */
    private int expireSeconds = 600;
}
