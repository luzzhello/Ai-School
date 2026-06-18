package org.ruoyi.system.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.constant.GlobalConstants;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.redis.utils.RedisUtils;
import org.ruoyi.common.social.config.properties.SocialLoginConfigProperties;
import org.ruoyi.common.social.config.properties.SocialProperties;
import org.ruoyi.system.config.properties.WxMpProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 微信公众号 API 客户端
 */
@Component
@RequiredArgsConstructor
public class WeChatMpApiClient {

    private final SocialProperties socialProperties;

    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={}&secret={}";
    private static final String CREATE_QRCODE_URL = "https://api.weixin.qq.com/cgi-bin/qrcode/create?access_token={}";
    private static final String SHOW_QRCODE_URL = "https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket={}";

    private final WxMpProperties wxMpProperties;

    /**
     * 校验微信服务器签名
     */
    public boolean checkSignature(String signature, String timestamp, String nonce) {
        if (StrUtil.hasBlank(wxMpProperties.getToken(), signature, timestamp, nonce)) {
            return false;
        }
        String[] arr = new String[]{wxMpProperties.getToken(), timestamp, nonce};
        java.util.Arrays.sort(arr);
        String content = String.join("", arr);
        return DigestUtil.sha1Hex(content).equalsIgnoreCase(signature);
    }

    /**
     * 创建带场景值的临时二维码
     */
    public QrCodeCreateResult createTempQrCode(String sceneStr) {
        String accessToken = getAccessToken();
        JSONObject body = JSONUtil.createObj()
            .set("expire_seconds", wxMpProperties.getExpireSeconds())
            .set("action_name", "QR_STR_SCENE")
            .set("action_info", JSONUtil.createObj()
                .set("scene", JSONUtil.createObj().set("scene_str", sceneStr)));

        String response = HttpRequest.post(StrUtil.format(CREATE_QRCODE_URL, accessToken))
            .body(body.toString())
            .timeout(10000)
            .execute()
            .body();
        JSONObject json = JSONUtil.parseObj(response);
        if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
            throw new ServiceException("微信二维码创建失败：" + json.getStr("errmsg", response));
        }
        String ticket = json.getStr("ticket");
        String encodedTicket = URLEncoder.encode(ticket, StandardCharsets.UTF_8);
        return new QrCodeCreateResult(ticket, StrUtil.format(SHOW_QRCODE_URL, encodedTicket));
    }

    private String getAccessToken() {
        String cached = RedisUtils.getCacheObject(GlobalConstants.WX_MP_ACCESS_TOKEN_KEY);
        if (StrUtil.isNotBlank(cached)) {
            return cached;
        }
        String appId = resolveAppId();
        String appSecret = resolveAppSecret();
        String response = HttpUtil.get(StrUtil.format(ACCESS_TOKEN_URL, appId, appSecret));
        JSONObject json = JSONUtil.parseObj(response);
        if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
            throw new ServiceException("获取微信 access_token 失败：" + json.getStr("errmsg", response));
        }
        String accessToken = json.getStr("access_token");
        int expiresIn = json.getInt("expires_in", 7200);
        RedisUtils.setCacheObject(
            GlobalConstants.WX_MP_ACCESS_TOKEN_KEY,
            accessToken,
            Duration.ofSeconds(Math.max(300, expiresIn - 300))
        );
        return accessToken;
    }

    public String resolveAppId() {
        if (StrUtil.isNotBlank(wxMpProperties.getAppId()) && !isPlaceholder(wxMpProperties.getAppId())) {
            return wxMpProperties.getAppId();
        }
        SocialLoginConfigProperties mp = getWechatMpConfig();
        if (mp != null && StrUtil.isNotBlank(mp.getClientId()) && !isPlaceholder(mp.getClientId())) {
            return mp.getClientId();
        }
        throw new ServiceException("未配置微信公众号 AppId，请在 wx.mp.app-id 或 justauth.type.wechat_mp 中配置");
    }

    public String resolveAppSecret() {
        if (StrUtil.isNotBlank(wxMpProperties.getAppSecret()) && !isPlaceholder(wxMpProperties.getAppSecret())) {
            return wxMpProperties.getAppSecret();
        }
        SocialLoginConfigProperties mp = getWechatMpConfig();
        if (mp != null && StrUtil.isNotBlank(mp.getClientSecret()) && !isPlaceholder(mp.getClientSecret())) {
            return mp.getClientSecret();
        }
        throw new ServiceException("未配置微信公众号 AppSecret，请在 wx.mp.app-secret 或 justauth.type.wechat_mp 中配置");
    }

    private SocialLoginConfigProperties getWechatMpConfig() {
        return socialProperties.getType() == null ? null : socialProperties.getType().get("wechat_mp");
    }

    private boolean isPlaceholder(String value) {
        return StrUtil.contains(value, '*') || StrUtil.containsIgnoreCase(value, "your")
            || StrUtil.containsIgnoreCase(value, "change");
    }

    public record QrCodeCreateResult(String ticket, String qrCodeUrl) {
    }
}
