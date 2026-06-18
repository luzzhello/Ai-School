package org.ruoyi.controller.usercenter;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.usercenter.PayOrderQueryRequest;
import org.ruoyi.domain.vo.usercenter.PayOrderDetailVo;
import org.ruoyi.domain.vo.usercenter.PayOrderVo;
import org.ruoyi.service.usercenter.IPayOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户支付订单
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/usercenter/orders")
public class PayOrderController {

    private final IPayOrderService payOrderService;

    @GetMapping
    public TableDataInfo<PayOrderVo> list(PayOrderQueryRequest query, PageQuery pageQuery) {
        return payOrderService.listOrders(requireUserId(), query, pageQuery);
    }

    @GetMapping("/{orderNo}")
    public R<PayOrderDetailVo> detail(@PathVariable String orderNo) {
        return R.ok(payOrderService.getOrderDetail(requireUserId(), orderNo));
    }

    @PostMapping("/{orderNo}/close")
    public R<Void> close(@PathVariable String orderNo) {
        payOrderService.closeOrder(requireUserId(), orderNo);
        return R.ok();
    }

    private Long requireUserId() {
        if (!LoginHelper.isLogin()) {
            throw new ServiceException("请先登录");
        }
        return LoginHelper.getUserId();
    }
}
