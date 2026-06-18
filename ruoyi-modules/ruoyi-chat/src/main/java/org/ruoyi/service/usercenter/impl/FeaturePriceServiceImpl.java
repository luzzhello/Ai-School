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
import org.ruoyi.domain.bo.usercenter.UcFeaturePriceBo;
import org.ruoyi.domain.entity.usercenter.UcFeaturePrice;
import org.ruoyi.domain.vo.usercenter.UcFeaturePriceVo;
import org.ruoyi.mapper.usercenter.UcFeaturePriceMapper;
import org.ruoyi.service.usercenter.IFeaturePriceService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Service
public class FeaturePriceServiceImpl implements IFeaturePriceService {

    private final UcFeaturePriceMapper baseMapper;

    @Override
    public UcFeaturePriceVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public UcFeaturePrice requireEnabledByCode(String featureCode) {
        LambdaQueryWrapper<UcFeaturePrice> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcFeaturePrice::getFeatureCode, featureCode);
        lqw.eq(UcFeaturePrice::getStatus, "0");
        lqw.last("LIMIT 1");
        UcFeaturePrice price = baseMapper.selectOne(lqw);
        if (price == null) {
            throw new ServiceException("功能定价未配置或已停用：" + featureCode);
        }
        return price;
    }

    @Override
    public TableDataInfo<UcFeaturePriceVo> queryPageList(UcFeaturePriceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<UcFeaturePrice> lqw = buildQueryWrapper(bo);
        Page<UcFeaturePriceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<UcFeaturePriceVo> queryList(UcFeaturePriceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<UcFeaturePrice> buildQueryWrapper(UcFeaturePriceBo bo) {
        LambdaQueryWrapper<UcFeaturePrice> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(UcFeaturePrice::getSortOrder);
        lqw.orderByAsc(UcFeaturePrice::getId);
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getFeatureName()), UcFeaturePrice::getFeatureName, bo.getFeatureName());
            lqw.eq(StringUtils.isNotBlank(bo.getFeatureCode()), UcFeaturePrice::getFeatureCode, bo.getFeatureCode());
            lqw.eq(StringUtils.isNotBlank(bo.getCategory()), UcFeaturePrice::getCategory, bo.getCategory());
            lqw.eq(StringUtils.isNotBlank(bo.getPriceType()), UcFeaturePrice::getPriceType, bo.getPriceType());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), UcFeaturePrice::getStatus, bo.getStatus());
        }
        return lqw;
    }

    @Override
    public Boolean insertByBo(UcFeaturePriceBo bo) {
        UcFeaturePrice add = MapstructUtils.convert(bo, UcFeaturePrice.class);
        validEntityBeforeSave(add, true);
        if (StringUtils.isBlank(add.getStatus())) {
            add.setStatus("0");
        }
        if (add.getSortOrder() == null) {
            add.setSortOrder(0);
        }
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(UcFeaturePriceBo bo) {
        UcFeaturePrice update = MapstructUtils.convert(bo, UcFeaturePrice.class);
        validEntityBeforeSave(update, false);
        return baseMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    private void validEntityBeforeSave(UcFeaturePrice entity, boolean isInsert) {
        LambdaQueryWrapper<UcFeaturePrice> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcFeaturePrice::getFeatureCode, entity.getFeatureCode());
        if (!isInsert && entity.getId() != null) {
            lqw.ne(UcFeaturePrice::getId, entity.getId());
        }
        if (baseMapper.exists(lqw)) {
            throw new ServiceException("功能编码已存在：" + entity.getFeatureCode());
        }
    }
}
