package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcMembershipFeatureQuotaBo;
import org.ruoyi.domain.entity.usercenter.UcMembershipFeatureQuota;
import org.ruoyi.domain.vo.usercenter.UcMembershipFeatureQuotaVo;
import org.ruoyi.mapper.usercenter.UcMembershipFeatureQuotaMapper;
import org.ruoyi.service.usercenter.IMembershipFeatureQuotaService;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class MembershipFeatureQuotaServiceImpl implements IMembershipFeatureQuotaService {

    private static final int UNLIMITED = -1;

    private final UcMembershipFeatureQuotaMapper baseMapper;

    private volatile Map<String, Map<String, Integer>> limitCache = Collections.emptyMap();

    @PostConstruct
    public void initLimitCache() {
        refreshLimitCache();
    }

    @Override
    public void refreshLimitCache() {
        LambdaQueryWrapper<UcMembershipFeatureQuota> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcMembershipFeatureQuota::getStatus, "0");
        lqw.eq(UcMembershipFeatureQuota::getIsCategory, "0");
        lqw.isNotNull(UcMembershipFeatureQuota::getFeatureCode);
        lqw.ne(UcMembershipFeatureQuota::getFeatureCode, "");
        List<UcMembershipFeatureQuota> rows = baseMapper.selectList(lqw);

        Map<String, Map<String, Integer>> next = new HashMap<>();
        for (UcMembershipFeatureQuota row : rows) {
            String code = row.getFeatureCode().trim().toLowerCase();
            putPlanLimit(next, UserCenterConstants.PLAN_WEEK, code, row.getWeekLimit());
            putPlanLimit(next, UserCenterConstants.PLAN_MONTH, code, row.getMonthLimit());
            putPlanLimit(next, UserCenterConstants.PLAN_YEAR, code, row.getYearLimit());
        }
        limitCache = Collections.unmodifiableMap(next);
    }

    @Override
    public Integer resolveLimit(String planCode, String featureCode) {
        if (UserCenterConstants.PLAN_FREE.equals(planCode) || StringUtils.isBlank(featureCode)) {
            return null;
        }
        Map<String, Integer> planLimits = limitCache.get(planCode);
        if (planLimits == null) {
            return null;
        }
        String code = featureCode.trim().toLowerCase();
        Integer limit = planLimits.get(code);
        if (limit != null) {
            return limit;
        }
        String alias = LIMIT_ALIAS.get(code);
        return alias == null ? null : planLimits.get(alias);
    }

    @Override
    public boolean isUnlimited(Integer limit) {
        return limit != null && limit == UNLIMITED;
    }

    @Override
    public UcMembershipFeatureQuotaVo queryById(Long quotaId) {
        return baseMapper.selectVoById(quotaId);
    }

    @Override
    public List<UcMembershipFeatureQuotaVo> listEnabled() {
        LambdaQueryWrapper<UcMembershipFeatureQuota> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcMembershipFeatureQuota::getStatus, "0");
        lqw.orderByAsc(UcMembershipFeatureQuota::getSortOrder);
        lqw.orderByAsc(UcMembershipFeatureQuota::getQuotaId);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public TableDataInfo<UcMembershipFeatureQuotaVo> queryPageList(UcMembershipFeatureQuotaBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<UcMembershipFeatureQuota> lqw = buildQueryWrapper(bo);
        Page<UcMembershipFeatureQuotaVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    private LambdaQueryWrapper<UcMembershipFeatureQuota> buildQueryWrapper(UcMembershipFeatureQuotaBo bo) {
        LambdaQueryWrapper<UcMembershipFeatureQuota> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(UcMembershipFeatureQuota::getSortOrder);
        lqw.orderByAsc(UcMembershipFeatureQuota::getQuotaId);
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getFeatureName()), UcMembershipFeatureQuota::getFeatureName, bo.getFeatureName());
            lqw.eq(StringUtils.isNotBlank(bo.getFeatureCode()), UcMembershipFeatureQuota::getFeatureCode, bo.getFeatureCode());
            lqw.eq(StringUtils.isNotBlank(bo.getIsCategory()), UcMembershipFeatureQuota::getIsCategory, bo.getIsCategory());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), UcMembershipFeatureQuota::getStatus, bo.getStatus());
        }
        return lqw;
    }

    @Override
    public Boolean insertByBo(UcMembershipFeatureQuotaBo bo) {
        UcMembershipFeatureQuota add = MapstructUtils.convert(bo, UcMembershipFeatureQuota.class);
        normalizeRow(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setQuotaId(add.getQuotaId());
            refreshLimitCache();
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(UcMembershipFeatureQuotaBo bo) {
        UcMembershipFeatureQuota update = MapstructUtils.convert(bo, UcMembershipFeatureQuota.class);
        normalizeRow(update);
        boolean flag = baseMapper.updateById(update) > 0;
        if (flag) {
            refreshLimitCache();
        }
        return flag;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        boolean flag = baseMapper.deleteByIds(ids) > 0;
        if (flag) {
            refreshLimitCache();
        }
        return flag;
    }

    private void normalizeRow(UcMembershipFeatureQuota row) {
        if (StringUtils.isBlank(row.getStatus())) {
            row.setStatus("0");
        }
        if (StringUtils.isBlank(row.getIsCategory())) {
            row.setIsCategory("0");
        }
        if (row.getSortOrder() == null) {
            row.setSortOrder(0);
        }
        if (StringUtils.isNotBlank(row.getFeatureCode())) {
            row.setFeatureCode(row.getFeatureCode().trim().toLowerCase());
        }
        if ("1".equals(row.getIsCategory())) {
            row.setFeatureCode(null);
            row.setWeekLimit(null);
            row.setMonthLimit(null);
            row.setYearLimit(null);
        }
    }

    private static void putPlanLimit(Map<String, Map<String, Integer>> cache, String planCode, String featureCode, Integer limit) {
        if (limit == null) {
            return;
        }
        cache.computeIfAbsent(planCode, k -> new HashMap<>()).put(featureCode, limit);
    }

    /** manual 变体未单独配置时，回退到对应 AI 编码的限额 */
    private static final Map<String, String> LIMIT_ALIAS = Map.of(
        "use_case_spec_manual", "use_case_spec_ai",
        "word_table_manual", "word_table_ai",
        "sql_three_line_sql", "sql_three_line_ai",
        "func_test_manual", "func_test_ai"
    );
}
