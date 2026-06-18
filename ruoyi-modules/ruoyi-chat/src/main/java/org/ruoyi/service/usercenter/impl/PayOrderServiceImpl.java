package org.ruoyi.service.usercenter.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.dto.request.usercenter.PayOrderQueryRequest;
import org.ruoyi.domain.entity.usercenter.UcWalletPayOrder;
import org.ruoyi.domain.vo.usercenter.PayOrderDetailVo;
import org.ruoyi.domain.vo.usercenter.PayOrderVo;
import org.ruoyi.domain.vo.usercenter.WalletAlipayCreateVo;
import org.ruoyi.domain.vo.usercenter.WalletAlipayQueryVo;
import org.ruoyi.mapper.usercenter.UcWalletPayOrderMapper;
import org.ruoyi.service.usercenter.IPayOrderService;
import org.ruoyi.service.usercenter.IUserMembershipService;
import org.ruoyi.service.usercenter.IUserWalletService;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.ruoyi.util.AlipayPagePayClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl implements IPayOrderService {

    private final AlipayPagePayClient alipayClient;
    private final UcWalletPayOrderMapper payOrderMapper;
    private final IUserWalletService walletService;
    @Lazy
    private final IUserMembershipService membershipService;

    @Override
    public boolean isAlipayEnabled() {
        return alipayClient.isReady();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletAlipayCreateVo createRechargeOrder(Long userId, BigDecimal amountYuan) {
        if (!isAlipayEnabled()) {
            throw new ServiceException("支付宝支付未配置，请使用模拟充值或联系管理员");
        }
        long coins = calcRechargeCoins(amountYuan);
        String orderNo = nextOrderNo();
        Date now = new Date();
        UcWalletPayOrder order = baseOrder(orderNo, userId, now);
        order.setOrderType(UserCenterConstants.ORDER_TYPE_RECHARGE);
        order.setOrderName("充值金币");
        order.setOrderContent("充值金币 " + coins + " 个");
        order.setAmountYuan(amountYuan);
        order.setCoins(coins);
        order.setTotalCoins(coins);
        payOrderMapper.insert(order);

        String payForm = alipayClient.createPagePayForm(orderNo, amountYuan, "码蚁校园金币充值");
        WalletAlipayCreateVo vo = new WalletAlipayCreateVo();
        vo.setOrderNo(orderNo);
        vo.setAmountYuan(amountYuan);
        vo.setCoins(coins);
        vo.setEnabled(true);
        vo.setPayForm(payForm);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UcWalletPayOrder createMembershipPendingOrder(String orderNo, Long userId, String planCode, String planName,
                                                         long totalCoins, long coinsUsed, BigDecimal cashYuan) {
        if (!isAlipayEnabled()) {
            throw new ServiceException("支付宝支付未配置，请先充值金币或联系管理员");
        }
        Date now = new Date();
        UcWalletPayOrder order = baseOrder(orderNo, userId, now);
        order.setOrderType(UserCenterConstants.ORDER_TYPE_MEMBERSHIP);
        order.setOrderName("购买会员");
        order.setOrderContent("开通" + planName);
        order.setPlanCode(planCode);
        order.setAmountYuan(cashYuan);
        order.setCoins(0L);
        order.setCoinsUsed(coinsUsed);
        order.setTotalCoins(totalCoins);
        String qrCode = alipayClient.createPrecreateQr(orderNo, cashYuan, "码蚁校园-" + planName);
        order.setQrCode(qrCode);
        payOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UcWalletPayOrder createPaidMembershipOrder(Long userId, String planCode, String planName,
                                                      long totalCoins, long coinsUsed, String orderNo) {
        Date now = new Date();
        UcWalletPayOrder order = baseOrder(orderNo, userId, now);
        order.setOrderType(UserCenterConstants.ORDER_TYPE_MEMBERSHIP);
        order.setOrderName("购买会员");
        order.setOrderContent("开通" + planName);
        order.setPlanCode(planCode);
        order.setAmountYuan(BigDecimal.ZERO);
        order.setCoins(0L);
        order.setCoinsUsed(coinsUsed);
        order.setTotalCoins(totalCoins);
        order.setStatus(UserCenterConstants.PAY_ORDER_PAID);
        order.setPayTime(now);
        payOrderMapper.insert(order);
        return order;
    }

    @Override
    public WalletAlipayQueryVo queryOrder(Long userId, String orderNo) {
        UcWalletPayOrder order = requireOwnedOrder(userId, orderNo);
        refreshOrderExpire(order);
        WalletAlipayQueryVo vo = new WalletAlipayQueryVo();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        if (UserCenterConstants.PAY_ORDER_PAID.equals(order.getStatus())) {
            if (UserCenterConstants.ORDER_TYPE_RECHARGE.equals(order.getOrderType())) {
                vo.setAddedCoins(order.getCoins());
            }
            vo.setBalance(walletService.getBalance(userId));
        }
        return vo;
    }

    @Override
    public PayOrderDetailVo getOrderDetail(Long userId, String orderNo) {
        UcWalletPayOrder order = requireOwnedOrder(userId, orderNo);
        refreshOrderExpire(order);
        PayOrderDetailVo vo = BeanUtil.copyProperties(order, PayOrderDetailVo.class);
        vo.setRemainSeconds(calcRemainSeconds(order));
        return vo;
    }

    @Override
    public TableDataInfo<PayOrderVo> listOrders(Long userId, PayOrderQueryRequest query, PageQuery pageQuery) {
        return TenantHelper.ignore(() -> {
            LambdaQueryWrapper<UcWalletPayOrder> lqw = Wrappers.lambdaQuery();
            lqw.eq(UcWalletPayOrder::getUserId, userId);
            if (query != null) {
                lqw.like(StringUtils.isNotBlank(query.getOrderNo()), UcWalletPayOrder::getOrderNo, query.getOrderNo());
                lqw.eq(StringUtils.isNotBlank(query.getStatus()), UcWalletPayOrder::getStatus, query.getStatus());
                lqw.eq(StringUtils.isNotBlank(query.getOrderType()), UcWalletPayOrder::getOrderType, query.getOrderType());
                lqw.like(StringUtils.isNotBlank(query.getOrderName()), UcWalletPayOrder::getOrderName, query.getOrderName());
            }
            lqw.orderByDesc(UcWalletPayOrder::getCreateTime);
            Page<UcWalletPayOrder> page = payOrderMapper.selectPage(pageQuery.build(), lqw);
            page.getRecords().forEach(this::refreshOrderExpire);
            Page<PayOrderVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            voPage.setRecords(BeanUtil.copyToList(page.getRecords(), PayOrderVo.class));
            return TableDataInfo.build(voPage);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeOrder(Long userId, String orderNo) {
        UcWalletPayOrder order = requireOwnedOrder(userId, orderNo);
        if (!UserCenterConstants.PAY_ORDER_PENDING.equals(order.getStatus())) {
            throw new ServiceException("仅待支付订单可关闭");
        }
        closeAndRefund(order, UserCenterConstants.PAY_ORDER_CLOSED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleNotify(HttpServletRequest request) {
        Map<String, String> params = toParamMap(request);
        if (!alipayClient.verifyNotify(params)) {
            log.warn("支付宝异步通知验签失败");
            return "failure";
        }
        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return "success";
        }
        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");
        if (StrUtil.hasBlank(orderNo, tradeNo, totalAmount)) {
            return "failure";
        }
        try {
            completePaidOrder(orderNo, tradeNo, new BigDecimal(totalAmount));
            return "success";
        }
        catch (Exception e) {
            log.error("处理支付宝回调失败 orderNo={}: {}", orderNo, e.getMessage());
            return "failure";
        }
    }

    @Override
    public void refreshOrderExpire(UcWalletPayOrder order) {
        if (order == null || !UserCenterConstants.PAY_ORDER_PENDING.equals(order.getStatus())) {
            return;
        }
        if (order.getExpireTime() != null && order.getExpireTime().before(new Date())) {
            closeAndRefund(order, UserCenterConstants.PAY_ORDER_EXPIRED);
        }
    }

    private void completePaidOrder(String orderNo, String tradeNo, BigDecimal paidAmount) {
        TenantHelper.ignore(() -> {
            UcWalletPayOrder order = requireOrder(orderNo);
            if (UserCenterConstants.PAY_ORDER_PAID.equals(order.getStatus())) {
                return null;
            }
            if (!UserCenterConstants.PAY_ORDER_PENDING.equals(order.getStatus())) {
                throw new ServiceException("订单状态异常");
            }
            BigDecimal orderAmount = order.getAmountYuan().setScale(2, RoundingMode.HALF_UP);
            if (orderAmount.compareTo(paidAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new ServiceException("支付金额与订单不一致");
            }
            order.setStatus(UserCenterConstants.PAY_ORDER_PAID);
            order.setTradeNo(tradeNo);
            Date now = new Date();
            order.setPayTime(now);
            order.setUpdateTime(now);
            payOrderMapper.updateById(order);

            if (UserCenterConstants.ORDER_TYPE_MEMBERSHIP.equals(order.getOrderType())) {
                membershipService.activateByPlanCode(order.getUserId(), order.getPlanCode(), order.getOrderNo());
            }
            else {
                walletService.changeBalance(
                    order.getUserId(),
                    order.getCoins(),
                    UserCenterConstants.BIZ_RECHARGE,
                    orderNo,
                    "支付宝充值 " + order.getAmountYuan() + " 元，到账 " + order.getCoins() + " 金币"
                );
            }
            return null;
        });
    }

    private void closeAndRefund(UcWalletPayOrder order, String status) {
        if (!UserCenterConstants.PAY_ORDER_PENDING.equals(order.getStatus())) {
            return;
        }
        order.setStatus(status);
        order.setUpdateTime(new Date());
        payOrderMapper.updateById(order);
        long refund = order.getCoinsUsed() == null ? 0L : order.getCoinsUsed();
        if (refund > 0) {
            walletService.changeBalance(
                order.getUserId(),
                refund,
                UserCenterConstants.BIZ_MEMBERSHIP_REFUND,
                order.getOrderNo(),
                "订单关闭退还抵扣金币 " + refund
            );
        }
    }

    private UcWalletPayOrder baseOrder(String orderNo, Long userId, Date now) {
        UcWalletPayOrder order = new UcWalletPayOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStatus(UserCenterConstants.PAY_ORDER_PENDING);
        order.setPayChannel("ALIPAY");
        order.setCoinsUsed(0L);
        order.setTotalCoins(0L);
        order.setTenantId(LoginHelper.getTenantId());
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setExpireTime(addMinutes(now, UserCenterConstants.PAY_ORDER_EXPIRE_MINUTES));
        return order;
    }

    private UcWalletPayOrder requireOrder(String orderNo) {
        LambdaQueryWrapper<UcWalletPayOrder> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcWalletPayOrder::getOrderNo, orderNo);
        UcWalletPayOrder order = payOrderMapper.selectOne(lqw);
        if (order == null) {
            throw new ServiceException("支付订单不存在");
        }
        return order;
    }

    private UcWalletPayOrder requireOwnedOrder(Long userId, String orderNo) {
        UcWalletPayOrder order = requireOrder(orderNo);
        if (!userId.equals(order.getUserId())) {
            throw new ServiceException("无权操作该订单");
        }
        return order;
    }

    private long calcRechargeCoins(BigDecimal amountYuan) {
        long coins = amountYuan.multiply(BigDecimal.valueOf(UserCenterConstants.COINS_PER_YUAN))
            .setScale(0, RoundingMode.DOWN)
            .longValue();
        if (coins <= 0) {
            throw new ServiceException("充值金额过小");
        }
        return coins;
    }

    private String nextOrderNo() {
        return String.valueOf(System.currentTimeMillis()) + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private Date addMinutes(Date base, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(base);
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    private Long calcRemainSeconds(UcWalletPayOrder order) {
        if (order.getExpireTime() == null) {
            return 0L;
        }
        long diff = (order.getExpireTime().getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(0L, diff);
    }

    private Map<String, String> toParamMap(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                continue;
            }
            params.put(entry.getKey(), values[0]);
        }
        return params;
    }
}
