package org.ruoyi.service.usercenter;

import jakarta.servlet.http.HttpServletRequest;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.domain.dto.request.usercenter.PayOrderQueryRequest;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.entity.usercenter.UcWalletPayOrder;
import org.ruoyi.domain.vo.usercenter.PayOrderDetailVo;
import org.ruoyi.domain.vo.usercenter.PayOrderVo;
import org.ruoyi.domain.vo.usercenter.WalletAlipayCreateVo;
import org.ruoyi.domain.vo.usercenter.WalletAlipayQueryVo;

import java.math.BigDecimal;

public interface IPayOrderService {

    boolean isAlipayEnabled();

    WalletAlipayCreateVo createRechargeOrder(Long userId, BigDecimal amountYuan);

    UcWalletPayOrder createMembershipPendingOrder(String orderNo, Long userId, String planCode, String planName,
                                                  long totalCoins, long coinsUsed, BigDecimal cashYuan);

    UcWalletPayOrder createPaidMembershipOrder(Long userId, String planCode, String planName,
                                             long totalCoins, long coinsUsed, String orderNo);

    WalletAlipayQueryVo queryOrder(Long userId, String orderNo);

    PayOrderDetailVo getOrderDetail(Long userId, String orderNo);

    TableDataInfo<PayOrderVo> listOrders(Long userId, PayOrderQueryRequest query, PageQuery pageQuery);

    void closeOrder(Long userId, String orderNo);

    String handleNotify(HttpServletRequest request);

    void refreshOrderExpire(UcWalletPayOrder order);
}
