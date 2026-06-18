package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcMembershipPlanBo;
import org.ruoyi.domain.vo.usercenter.UcMembershipPlanVo;

import java.util.Collection;
import java.util.List;

public interface IMembershipPlanService {

    UcMembershipPlanVo queryById(Long planId);

    TableDataInfo<UcMembershipPlanVo> queryPageList(UcMembershipPlanBo bo, PageQuery pageQuery);

    List<UcMembershipPlanVo> queryList(UcMembershipPlanBo bo);

    Boolean insertByBo(UcMembershipPlanBo bo);

    Boolean updateByBo(UcMembershipPlanBo bo);

    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
