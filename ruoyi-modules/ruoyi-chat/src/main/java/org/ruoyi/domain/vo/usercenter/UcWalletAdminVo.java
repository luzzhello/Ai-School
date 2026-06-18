package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class UcWalletAdminVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long walletId;

    private Long userId;

    private String userName;

    private Long balance;

    private Long frozenBalance;

    private Date createTime;

    private Date updateTime;
}
