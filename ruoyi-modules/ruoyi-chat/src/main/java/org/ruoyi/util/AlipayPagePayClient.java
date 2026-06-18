package org.ruoyi.util;

import cn.hutool.core.util.StrUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.config.properties.AlipayPayProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付客户端（支持公钥模式 / 证书模式）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayPagePayClient {

    private static final String CHARSET = "UTF-8";
    private static final String FORMAT = "json";
    private static final String SIGN_TYPE = "RSA2";
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";

    private final AlipayPayProperties properties;

    public boolean isReady() {
        if (!properties.isEnabled()) {
            return false;
        }
        if (StrUtil.hasBlank(properties.getAppId(), properties.getPrivateKey(),
            properties.getNotifyUrl(), properties.getReturnUrl())) {
            return false;
        }
        if (properties.isCertMode()) {
            return StrUtil.isAllNotBlank(
                properties.getAppCertPath(),
                properties.getAlipayCertPath(),
                properties.getRootCertPath()
            );
        }
        return StrUtil.isNotBlank(properties.getAlipayPublicKey());
    }

    public String createPagePayForm(String orderNo, BigDecimal amountYuan, String subject) {
        ensureReady();
        try {
            AlipayClient client = buildClient();
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(properties.getNotifyUrl());
            request.setReturnUrl(properties.getReturnUrl());
            String bizContent = """
                {"out_trade_no":"%s","total_amount":"%s","subject":"%s","product_code":"%s"}
                """.formatted(orderNo, amountYuan.toPlainString(), escapeJson(subject), PRODUCT_CODE).trim();
            request.setBizContent(bizContent);
            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (!response.isSuccess()) {
                throw new ServiceException("支付宝下单失败：" + response.getSubMsg());
            }
            return response.getBody();
        }
        catch (AlipayApiException e) {
            log.error("支付宝下单异常: {}", e.getMessage());
            throw new ServiceException("支付宝下单失败，请稍后重试");
        }
    }

    /**
     * 当面付预下单，返回扫码支付二维码内容
     */
    public String createPrecreateQr(String orderNo, BigDecimal amountYuan, String subject) {
        ensureReady();
        try {
            AlipayClient client = buildClient();
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setNotifyUrl(properties.getNotifyUrl());
            String bizContent = """
                {"out_trade_no":"%s","total_amount":"%s","subject":"%s"}
                """.formatted(orderNo, amountYuan.toPlainString(), escapeJson(subject)).trim();
            request.setBizContent(bizContent);
            AlipayTradePrecreateResponse response = client.execute(request);
            if (!response.isSuccess()) {
                throw new ServiceException("支付宝扫码下单失败：" + response.getSubMsg());
            }
            return response.getQrCode();
        }
        catch (AlipayApiException e) {
            log.error("支付宝扫码下单异常: {}", e.getMessage());
            throw new ServiceException("支付宝扫码下单失败，请稍后重试");
        }
    }

    public boolean verifyNotify(Map<String, String> params) {
        ensureReady();
        try {
            if (properties.isCertMode()) {
                return AlipaySignature.rsaCertCheckV1(
                    params,
                    properties.getAlipayCertPath(),
                    CHARSET,
                    SIGN_TYPE
                );
            }
            return AlipaySignature.rsaCheckV1(
                params,
                properties.getAlipayPublicKey(),
                CHARSET,
                SIGN_TYPE
            );
        }
        catch (AlipayApiException e) {
            log.warn("支付宝回调验签失败: {}", e.getMessage());
            return false;
        }
    }

    private AlipayClient buildClient() throws AlipayApiException {
        if (properties.isCertMode()) {
            CertAlipayRequest certRequest = new CertAlipayRequest();
            certRequest.setServerUrl(properties.getGatewayUrl());
            certRequest.setAppId(properties.getAppId());
            certRequest.setPrivateKey(properties.getPrivateKey());
            certRequest.setFormat(FORMAT);
            certRequest.setCharset(CHARSET);
            certRequest.setSignType(SIGN_TYPE);
            certRequest.setCertPath(properties.getAppCertPath());
            certRequest.setAlipayPublicCertPath(properties.getAlipayCertPath());
            certRequest.setRootCertPath(properties.getRootCertPath());
            return new DefaultAlipayClient(certRequest);
        }
        return new DefaultAlipayClient(
            properties.getGatewayUrl(),
            properties.getAppId(),
            properties.getPrivateKey(),
            FORMAT,
            CHARSET,
            properties.getAlipayPublicKey(),
            SIGN_TYPE
        );
    }

    private void ensureReady() {
        if (!isReady()) {
            throw new ServiceException("支付宝支付未配置，请联系管理员或使用模拟充值");
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
