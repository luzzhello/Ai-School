package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 用户金币钱包 uc_wallet（按 user_id 唯一，不走租户隔离）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("uc_wallet")
public class UcWallet extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "wallet_id")
    private Long walletId;

    private Long userId;

    private Long balance;

    private Long frozenBalance;

    private String tenantId;
}
