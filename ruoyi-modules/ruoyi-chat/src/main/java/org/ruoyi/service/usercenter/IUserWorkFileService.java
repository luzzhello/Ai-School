package org.ruoyi.service.usercenter;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.dto.request.usercenter.WorkFileQueryRequest;
import org.ruoyi.domain.dto.request.usercenter.WorkFileSaveRequest;
import org.ruoyi.domain.vo.usercenter.UcWorkFileVo;

public interface IUserWorkFileService {

    TableDataInfo<UcWorkFileVo> page(Long userId, WorkFileQueryRequest query, PageQuery pageQuery);

    UcWorkFileVo detail(Long userId, Long fileId);

    Long save(Long userId, WorkFileSaveRequest request);

    void remove(Long userId, Long fileId);
}
