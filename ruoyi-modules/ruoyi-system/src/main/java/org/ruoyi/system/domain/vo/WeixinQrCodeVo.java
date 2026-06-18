package org.ruoyi.system.domain.vo;

import lombok.Data;

/**
 * 微信登录二维码
 */
@Data
public class WeixinQrCodeVo {

    /**
     * 轮询 ticket（场景值）
     */
    private String ticket;

    /**
     * 二维码图片地址
     */
    private String qrCodeUrl;
}
