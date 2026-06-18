package org.ruoyi.domain.vo.usercenter;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
public class PayOrderDetailVo extends PayOrderVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private String qrCode;

    /** 剩余支付秒数 */
    private Long remainSeconds;
}
