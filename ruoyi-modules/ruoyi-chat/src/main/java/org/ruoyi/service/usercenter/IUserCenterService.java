package org.ruoyi.service.usercenter;

import org.ruoyi.domain.vo.usercenter.UserCenterOverviewVo;

public interface IUserCenterService {

    UserCenterOverviewVo overview(Long userId, String username, String nickName, String inviteCode);

    UserCenterOverviewVo sidebar(Long userId, String username, String nickName, String inviteCode);
}
