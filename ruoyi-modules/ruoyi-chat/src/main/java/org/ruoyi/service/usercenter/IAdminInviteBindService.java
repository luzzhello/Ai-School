package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcInviteBindBo;
import org.ruoyi.domain.vo.usercenter.UcInviteBindVo;

public interface IAdminInviteBindService {

    TableDataInfo<UcInviteBindVo> queryPageList(UcInviteBindBo bo, PageQuery pageQuery);
}
