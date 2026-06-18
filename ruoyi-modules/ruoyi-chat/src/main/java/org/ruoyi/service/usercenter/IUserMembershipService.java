package org.ruoyi.service.usercenter;

import org.ruoyi.domain.entity.usercenter.UcUserMembership;
import org.ruoyi.domain.vo.usercenter.MembershipPurchaseCreateVo;
import org.ruoyi.domain.vo.usercenter.MembershipPurchasePreviewVo;
import org.ruoyi.domain.vo.usercenter.UcMembershipPlanVo;

import java.util.List;

public interface IUserMembershipService {

    UcUserMembership getActiveMembership(Long userId);

    List<UcMembershipPlanVo> listPlans(Long userId);

    void purchase(Long userId, String planCode);

    MembershipPurchasePreviewVo previewPurchase(Long userId, String planCode);

    MembershipPurchaseCreateVo createPurchaseOrder(Long userId, String planCode);

    void activateByPlanCode(Long userId, String planCode, String bizNo);
}
