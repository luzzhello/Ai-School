package org.ruoyi.domain.dto.request.usercenter;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletRechargeRequest {

    /** 充值金额（元） */
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额至少 0.01 元")
    private BigDecimal amountYuan;
}
