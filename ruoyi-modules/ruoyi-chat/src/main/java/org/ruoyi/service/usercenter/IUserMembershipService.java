package org.ruoyi.service.usercenter;

import org.ruoyi.domain.entity.usercenter.UcUserMembership;
import org.ruoyi.domain.vo.usercenter.UcMembershipPlanVo;

import java.util.List;

public interface IUserMembershipService {

    UcUserMembership getActiveMembership(Long userId);

    List<UcMembershipPlanVo> listPlans(Long userId);

    void purchase(Long userId, String planCode);
}
