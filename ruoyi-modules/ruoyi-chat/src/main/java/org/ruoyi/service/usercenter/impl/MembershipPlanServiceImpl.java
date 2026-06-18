package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcMembershipPlanBo;
import org.ruoyi.domain.entity.usercenter.UcMembershipPlan;
import org.ruoyi.domain.vo.usercenter.UcMembershipPlanVo;
import org.ruoyi.mapper.usercenter.UcMembershipPlanMapper;
import org.ruoyi.service.usercenter.IMembershipPlanService;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Service
public class MembershipPlanServiceImpl implements IMembershipPlanService {

    private final UcMembershipPlanMapper baseMapper;

    @Override
    public UcMembershipPlanVo queryById(Long planId) {
        return baseMapper.selectVoById(planId);
    }

    @Override
    public TableDataInfo<UcMembershipPlanVo> queryPageList(UcMembershipPlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<UcMembershipPlan> lqw = buildQueryWrapper(bo);
        Page<UcMembershipPlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<UcMembershipPlanVo> queryList(UcMembershipPlanBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<UcMembershipPlan> buildQueryWrapper(UcMembershipPlanBo bo) {
        LambdaQueryWrapper<UcMembershipPlan> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(UcMembershipPlan::getSortOrder);
        lqw.orderByAsc(UcMembershipPlan::getPlanId);
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getPlanName()), UcMembershipPlan::getPlanName, bo.getPlanName());
            lqw.eq(StringUtils.isNotBlank(bo.getPlanCode()), UcMembershipPlan::getPlanCode, bo.getPlanCode());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), UcMembershipPlan::getStatus, bo.getStatus());
        }
        return lqw;
    }

    @Override
    public Boolean insertByBo(UcMembershipPlanBo bo) {
        UcMembershipPlan add = MapstructUtils.convert(bo, UcMembershipPlan.class);
        validEntityBeforeSave(add, true);
        if (StringUtils.isBlank(add.getStatus())) {
            add.setStatus("0");
        }
        if (add.getSortOrder() == null) {
            add.setSortOrder(0);
        }
        if (add.getPriceCoins() == null) {
            add.setPriceCoins(0L);
        }
        if (add.getDurationDays() == null) {
            add.setDurationDays(0);
        }
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setPlanId(add.getPlanId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(UcMembershipPlanBo bo) {
        UcMembershipPlan update = MapstructUtils.convert(bo, UcMembershipPlan.class);
        validEntityBeforeSave(update, false);
        return baseMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            for (Long id : ids) {
                UcMembershipPlan plan = baseMapper.selectById(id);
                if (plan != null && UserCenterConstants.PLAN_FREE.equals(plan.getPlanCode())) {
                    throw new ServiceException("免费会员套餐不可删除");
                }
            }
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    private void validEntityBeforeSave(UcMembershipPlan entity, boolean isInsert) {
        LambdaQueryWrapper<UcMembershipPlan> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcMembershipPlan::getPlanCode, entity.getPlanCode());
        if (!isInsert && entity.getPlanId() != null) {
            lqw.ne(UcMembershipPlan::getPlanId, entity.getPlanId());
        }
        if (baseMapper.exists(lqw)) {
            throw new ServiceException("套餐编码已存在：" + entity.getPlanCode());
        }
    }
}
