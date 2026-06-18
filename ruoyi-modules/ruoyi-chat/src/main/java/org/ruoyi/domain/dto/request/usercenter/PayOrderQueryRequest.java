package org.ruoyi.domain.dto.request.usercenter;

import lombok.Data;

@Data
public class PayOrderQueryRequest {

    /** 订单号（模糊） */
    private String orderNo;

    /** 订单状态：0待支付 1已支付 2已关闭 3已过期 */
    private String status;

    /** 订单类型：RECHARGE / MEMBERSHIP */
    private String orderType;

    /** 订单名称（模糊） */
    private String orderName;
}
