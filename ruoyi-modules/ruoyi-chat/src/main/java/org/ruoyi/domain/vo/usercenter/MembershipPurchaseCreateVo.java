package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class MembershipPurchaseCreateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否已纯金币开通完成 */
    private Boolean completed;

    private String orderNo;

    private String planCode;

    private String planName;

    private Long coinsUsed;

    private BigDecimal cashYuan;

    private String status;
}
