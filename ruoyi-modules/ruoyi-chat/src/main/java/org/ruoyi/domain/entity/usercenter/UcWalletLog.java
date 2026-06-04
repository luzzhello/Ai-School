package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 金币流水 uc_wallet_log
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("uc_wallet_log")
public class UcWalletLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id")
    private Long logId;

    private Long userId;

    private Long changeAmount;

    private Long balanceAfter;

    private String bizType;

    private String bizNo;

    private String description;
}
