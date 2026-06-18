package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcPayOrderBo;
import org.ruoyi.domain.vo.usercenter.UcPayOrderAdminVo;

public interface IAdminPayOrderService {

    TableDataInfo<UcPayOrderAdminVo> queryPageList(UcPayOrderBo bo, PageQuery pageQuery);
}
