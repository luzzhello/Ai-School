package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.usercenter.UcActivitySubmissionAuditBo;
import org.ruoyi.domain.bo.usercenter.UcActivitySubmissionBo;
import org.ruoyi.domain.vo.usercenter.UcActivitySubmissionVo;

public interface IAdminActivitySubmissionService {

    TableDataInfo<UcActivitySubmissionVo> queryPageList(UcActivitySubmissionBo bo, PageQuery pageQuery);

    UcActivitySubmissionVo queryById(Long id);

    void audit(UcActivitySubmissionAuditBo bo);
}
