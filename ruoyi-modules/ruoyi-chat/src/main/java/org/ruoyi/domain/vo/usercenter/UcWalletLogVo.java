package org.ruoyi.domain.vo.usercenter;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.domain.entity.usercenter.UcWalletLog;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 金币流水视图对象 uc_wallet_log
 */
@Data
@AutoMapper(target = UcWalletLog.class)
public class UcWalletLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long logId;

    private String bizType;

    private String bizNo;

    private String description;

    private Long changeAmount;

    private Long balanceAfter;

    private Date createTime;
}
