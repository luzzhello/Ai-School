package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcMembershipFeatureQuotaBo;
import org.ruoyi.domain.vo.usercenter.UcMembershipFeatureQuotaVo;

import java.util.Collection;
import java.util.List;

public interface IMembershipFeatureQuotaService {

    UcMembershipFeatureQuotaVo queryById(Long quotaId);

    List<UcMembershipFeatureQuotaVo> listEnabled();

    TableDataInfo<UcMembershipFeatureQuotaVo> queryPageList(UcMembershipFeatureQuotaBo bo, PageQuery pageQuery);

    Boolean insertByBo(UcMembershipFeatureQuotaBo bo);

    Boolean updateByBo(UcMembershipFeatureQuotaBo bo);

    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 解析会员套餐下某功能的每日次数限额
     *
     * @return -1 无限次；null 无会员配额（按金币计费）
     */
    Integer resolveLimit(String planCode, String featureCode);

    boolean isUnlimited(Integer limit);

    void refreshLimitCache();
}
