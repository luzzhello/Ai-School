package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcFeaturePriceBo;
import org.ruoyi.domain.entity.usercenter.UcFeaturePrice;
import org.ruoyi.domain.vo.usercenter.UcFeaturePriceVo;

import java.util.Collection;
import java.util.List;

public interface IFeaturePriceService {

    UcFeaturePriceVo queryById(Long id);

    UcFeaturePrice requireEnabledByCode(String featureCode);

    TableDataInfo<UcFeaturePriceVo> queryPageList(UcFeaturePriceBo bo, PageQuery pageQuery);

    List<UcFeaturePriceVo> queryList(UcFeaturePriceBo bo);

    Boolean insertByBo(UcFeaturePriceBo bo);

    Boolean updateByBo(UcFeaturePriceBo bo);

    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
