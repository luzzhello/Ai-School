package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.usercenter.UcWallet;
import org.ruoyi.domain.entity.usercenter.UcWalletLog;
import org.ruoyi.domain.vo.usercenter.UcWalletLogVo;
import org.ruoyi.mapper.usercenter.UcWalletLogMapper;
import org.ruoyi.mapper.usercenter.UcWalletMapper;
import org.ruoyi.service.usercenter.IUserWalletService;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UserWalletServiceImpl implements IUserWalletService {

    private final UcWalletMapper walletMapper;
    private final UcWalletLogMapper walletLogMapper;

    @Override
    public long getBalance(Long userId) {
        return getOrCreateWallet(userId).getBalance();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long recharge(Long userId, BigDecimal amountYuan) {
        long coins = amountYuan.multiply(BigDecimal.valueOf(UserCenterConstants.COINS_PER_YUAN))
            .setScale(0, RoundingMode.DOWN)
            .longValue();
        if (coins <= 0) {
            throw new ServiceException("充值金额过小");
        }
        String bizNo = "RCH" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        changeBalance(userId, coins, UserCenterConstants.BIZ_RECHARGE, bizNo,
            "充值 " + amountYuan + " 元，到账 " + coins + " 金币");
        return coins;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeBalance(Long userId, long changeAmount, String bizType, String bizNo, String description) {
        if (changeAmount == 0) {
            return;
        }
        UcWallet wallet = getOrCreateWallet(userId);
        long newBalance = wallet.getBalance() + changeAmount;
        if (newBalance < 0) {
            throw new ServiceException("金币余额不足");
        }
        wallet.setBalance(newBalance);
        int updated = walletMapper.updateById(wallet);
        if (updated == 0) {
            throw new ServiceException("余额更新失败，请重试");
        }
        UcWalletLog log = new UcWalletLog();
        log.setUserId(userId);
        log.setChangeAmount(changeAmount);
        log.setBalanceAfter(newBalance);
        log.setBizType(bizType);
        log.setBizNo(bizNo);
        log.setDescription(description);
        log.setTenantId(LoginHelper.getTenantId());
        walletLogMapper.insert(log);
    }

    @Override
    public TableDataInfo<UcWalletLogVo> listLogs(Long userId, PageQuery pageQuery) {
        LambdaQueryWrapper<UcWalletLog> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcWalletLog::getUserId, userId);
        lqw.orderByDesc(UcWalletLog::getCreateTime);
        Page<UcWalletLogVo> page = walletLogMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    private UcWallet getOrCreateWallet(Long userId) {
        LambdaQueryWrapper<UcWallet> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcWallet::getUserId, userId);
        UcWallet wallet = walletMapper.selectOne(lqw);
        if (wallet != null) {
            return wallet;
        }
        wallet = new UcWallet();
        wallet.setUserId(userId);
        wallet.setBalance(0L);
        wallet.setFrozenBalance(0L);
        wallet.setVersion(0L);
        wallet.setTenantId(LoginHelper.getTenantId());
        walletMapper.insert(wallet);
        return wallet;
    }
}
