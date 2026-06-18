package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class MembershipPurchasePreviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String planCode;
    private String planName;
    private Long priceCoins;
    private Long balance;
    private Long coinsUsed;
    private Long shortageCoins;
    private BigDecimal cashYuan;
    private Boolean sufficient;
}
