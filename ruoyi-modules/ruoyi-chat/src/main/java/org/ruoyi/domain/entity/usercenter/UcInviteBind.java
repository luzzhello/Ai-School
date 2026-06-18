package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("uc_invite_bind")
public class UcInviteBind implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long inviteeId;

    private Long inviterId;

    private String inviteCode;

    private Long coinsInviter;

    private Long coinsInvitee;

    private String monthKey;

    private Date createTime;
}
