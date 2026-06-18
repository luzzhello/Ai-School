package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcWalletLogBo;
import org.ruoyi.domain.vo.usercenter.UcWalletLogAdminVo;

public interface IAdminWalletLogService {

    TableDataInfo<UcWalletLogAdminVo> queryPageList(UcWalletLogBo bo, PageQuery pageQuery);
}
