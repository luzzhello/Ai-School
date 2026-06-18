package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("uc_wallet_pay_order")
public class UcWalletPayOrder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "order_id")
    private Long orderId;

    private String orderNo;

    /** RECHARGE / MEMBERSHIP */
    private String orderType;

    private Long userId;

    private BigDecimal amountYuan;

    /** 充值到账金币；会员订单为 0 */
    private Long coins;

    private String orderName;

    private String orderContent;

    private String planCode;

    /** 下单时已抵扣金币 */
    private Long coinsUsed;

    /** 会员订单总金币价 */
    private Long totalCoins;

    /** 0待支付 1已支付 2已关闭 */
    private String status;

    private String payChannel;

    private String tradeNo;

    private String qrCode;

    private String tenantId;

    private Date createTime;

    private Date payTime;

    private Date updateTime;

    private Date expireTime;
}
