package org.ruoyi.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝电脑网站支付配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay.pay")
public class AlipayPayProperties {

    /** 是否启用真实支付宝支付 */
    private boolean enabled = false;

    /**
     * 加签模式：key=公钥模式，cert=证书模式
     */
    private String signMode = "key";

    /** 应用 AppId */
    private String appId = "";

    /** 应用私钥（PKCS8） */
    private String privateKey = "";

    /** 公钥模式：支付宝公钥 */
    private String alipayPublicKey = "";

    /** 证书模式：应用公钥证书路径（.crt） */
    private String appCertPath = "";

    /** 证书模式：支付宝公钥证书路径（.crt） */
    private String alipayCertPath = "";

    /** 证书模式：支付宝根证书路径（.crt） */
    private String rootCertPath = "";

    /**
     * 网关地址
     * 沙箱：https://openapi-sandbox.dl.alipaydev.com/gateway.do
     * 正式：https://openapi.alipay.com/gateway.do
     */
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";

    /** 异步通知地址（公网可访问） */
    private String notifyUrl = "";

    /** 同步跳转地址（前端钱包页） */
    private String returnUrl = "";

    /** 订单标题 */
    private String subject = "码蚁校园金币充值";

    public boolean isCertMode() {
        return "cert".equalsIgnoreCase(signMode);
    }

    public boolean isKeyMode() {
        return !isCertMode();
    }
}
