package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcWalletBo;
import org.ruoyi.domain.vo.usercenter.UcWalletAdminVo;

public interface IAdminWalletService {

    TableDataInfo<UcWalletAdminVo> queryPageList(UcWalletBo bo, PageQuery pageQuery);
}
