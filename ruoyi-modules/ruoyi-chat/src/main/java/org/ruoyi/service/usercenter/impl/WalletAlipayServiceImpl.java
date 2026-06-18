package org.ruoyi.service.usercenter.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.vo.usercenter.WalletAlipayCreateVo;
import org.ruoyi.domain.vo.usercenter.WalletAlipayQueryVo;
import org.ruoyi.service.usercenter.IPayOrderService;
import org.ruoyi.service.usercenter.IWalletAlipayService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 兼容旧接口，委托给统一支付订单服务
 */
@Service
@RequiredArgsConstructor
public class WalletAlipayServiceImpl implements IWalletAlipayService {

    private final IPayOrderService payOrderService;

    @Override
    public boolean isEnabled() {
        return payOrderService.isAlipayEnabled();
    }

    @Override
    public WalletAlipayCreateVo createOrder(Long userId, BigDecimal amountYuan) {
        return payOrderService.createRechargeOrder(userId, amountYuan);
    }

    @Override
    public WalletAlipayQueryVo queryOrder(Long userId, String orderNo) {
        return payOrderService.queryOrder(userId, orderNo);
    }

    @Override
    public String handleNotify(HttpServletRequest request) {
        return payOrderService.handleNotify(request);
    }
}
