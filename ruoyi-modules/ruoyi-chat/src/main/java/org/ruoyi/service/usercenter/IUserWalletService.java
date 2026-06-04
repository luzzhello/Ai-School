package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.vo.usercenter.UcWalletLogVo;

import java.math.BigDecimal;

public interface IUserWalletService {

    long getBalance(Long userId);

    long recharge(Long userId, BigDecimal amountYuan);

    void changeBalance(Long userId, long changeAmount, String bizType, String bizNo, String description);

    TableDataInfo<UcWalletLogVo> listLogs(Long userId, PageQuery pageQuery);
}
