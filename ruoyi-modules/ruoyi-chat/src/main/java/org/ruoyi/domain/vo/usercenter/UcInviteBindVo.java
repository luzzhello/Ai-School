package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class UcInviteBindVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long inviteeId;

    private String inviteeName;

    private Long inviterId;

    private String inviterName;

    private String inviteCode;

    private Long coinsInviter;

    private Long coinsInvitee;

    private String monthKey;

    private Date createTime;
}
