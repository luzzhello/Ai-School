package org.ruoyi.domain.bo.usercenter;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
public class UcInviteBindBo extends BaseEntity {

    private Long inviterId;

    private Long inviteeId;

    private String inviteCode;

    private String monthKey;
}
