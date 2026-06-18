package org.ruoyi.system.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信扫码登录 ticket 状态
 */
@Data
public class WxQrTicketState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场景值（轮询 ticket）
     */
    private String scene;

    /**
     * 扫码用户 openid
     */
    private String openid;

    /**
     * waiting / scanned / logged
     */
    private String status;
}
