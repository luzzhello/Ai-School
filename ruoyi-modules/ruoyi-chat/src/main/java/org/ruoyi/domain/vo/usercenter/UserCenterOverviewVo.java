package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.util.Date;

/**
 * 个人中心概览
 */
@Data
public class UserCenterOverviewVo {

    private Long userId;

    private String username;

    private String nickName;

    private String avatar;

    private Date joinTime;

    private Integer usedDays;

    private String inviteCode;

    private Long coinBalance;

    private String membershipPlanCode;

    private String membershipPlanName;

    private Date membershipExpireTime;
}
