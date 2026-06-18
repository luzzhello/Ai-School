package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class UcWalletLogAdminVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long logId;

    private Long userId;

    private String userName;

    private String bizType;

    private String bizNo;

    private String description;

    private Long changeAmount;

    private Long balanceAfter;

    private Date createTime;
}
