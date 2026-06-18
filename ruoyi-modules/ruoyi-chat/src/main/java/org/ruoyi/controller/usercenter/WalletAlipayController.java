package org.ruoyi.controller.usercenter;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.usercenter.WalletRechargeRequest;
import org.ruoyi.domain.vo.usercenter.WalletAlipayCreateVo;
import org.ruoyi.domain.vo.usercenter.WalletAlipayQueryVo;
import org.ruoyi.service.usercenter.IWalletAlipayService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 钱包支付宝充值
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/usercenter/wallet/alipay")
public class WalletAlipayController {

    private final IWalletAlipayService walletAlipayService;

    @GetMapping("/enabled")
    public R<Map<String, Boolean>> enabled() {
        Map<String, Boolean> data = new HashMap<>(1);
        data.put("enabled", walletAlipayService.isEnabled());
        return R.ok(data);
    }

    @PostMapping("/create")
    public R<WalletAlipayCreateVo> create(@RequestBody @Valid WalletRechargeRequest request) {
        Long userId = requireLoginUserId();
        return R.ok(walletAlipayService.createOrder(userId, request.getAmountYuan()));
    }

    @GetMapping("/query")
    public R<WalletAlipayQueryVo> query(@RequestParam String orderNo) {
        Long userId = requireLoginUserId();
        return R.ok(walletAlipayService.queryOrder(userId, orderNo));
    }

    /**
     * 支付宝异步通知（无需登录）
     */
    @SaIgnore
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        return walletAlipayService.handleNotify(request);
    }

    private Long requireLoginUserId() {
        if (!LoginHelper.isLogin()) {
            throw new ServiceException("请先登录");
        }
        return LoginHelper.getUserId();
    }
}
