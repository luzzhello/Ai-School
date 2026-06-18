package org.ruoyi.service.usercenter;

import jakarta.servlet.http.HttpServletRequest;
import org.ruoyi.domain.vo.usercenter.WalletAlipayCreateVo;
import org.ruoyi.domain.vo.usercenter.WalletAlipayQueryVo;

import java.math.BigDecimal;

public interface IWalletAlipayService {

    boolean isEnabled();

    WalletAlipayCreateVo createOrder(Long userId, BigDecimal amountYuan);

    WalletAlipayQueryVo queryOrder(Long userId, String orderNo);

    String handleNotify(HttpServletRequest request);
}
