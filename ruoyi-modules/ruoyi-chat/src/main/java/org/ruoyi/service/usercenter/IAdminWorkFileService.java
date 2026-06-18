package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcWorkFileBo;
import org.ruoyi.domain.vo.usercenter.UcWorkFileAdminVo;

public interface IAdminWorkFileService {

    TableDataInfo<UcWorkFileAdminVo> queryPageList(UcWorkFileBo bo, PageQuery pageQuery);
}
