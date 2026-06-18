package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class UcPayOrderAdminVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;

    private Long userId;

    private String userName;

    private String orderNo;

    private String orderType;

    private String orderName;

    private String orderContent;

    private BigDecimal amountYuan;

    private Long coins;

    private Long coinsUsed;

    private Long totalCoins;

    private String status;

    private String payChannel;

    private String tradeNo;

    private String planCode;

    private Date createTime;

    private Date payTime;

    private Date expireTime;
}
