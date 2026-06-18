package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class WalletAlipayQueryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;

    /** 0待支付 1已支付 2已关闭 */
    private String status;

    private Long addedCoins;

    private Long balance;
}
