package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 用户金币钱包 uc_wallet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("uc_wallet")
public class UcWallet extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "wallet_id")
    private Long walletId;

    private Long userId;

    private Long balance;

    private Long frozenBalance;

    @Version
    private Long version;
}
