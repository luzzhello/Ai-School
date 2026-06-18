package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WalletAlipayCreateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;

    private BigDecimal amountYuan;

    private Long coins;

    /** 是否已启用支付宝 */
    private Boolean enabled;

    /** 电脑网站支付表单 HTML，前端写入后自动提交 */
    private String payForm;
}
