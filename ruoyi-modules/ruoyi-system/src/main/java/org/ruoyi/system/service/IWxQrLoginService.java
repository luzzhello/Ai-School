package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.WeixinQrCodeVo;
import org.ruoyi.system.domain.vo.WxQrLoginUserVo;

/**
 * 微信公众号扫码登录
 */
public interface IWxQrLoginService {

    /**
     * 生成登录二维码
     */
    WeixinQrCodeVo createLoginQrCode();

    /**
     * 轮询扫码登录结果
     */
    WxQrLoginUserVo pollLoginResult(String ticket, String clientId);

    /**
     * 处理微信扫码/关注事件
     */
    void handleScanEvent(String scene, String openid);
}
