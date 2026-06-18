package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

@Data
public class ActivityInviteInfoVo {

    private String inviteCode;

    private long monthlyEarned;

    private long monthlyCap;

    private boolean hasBound;

    private long rewardEach;
}
