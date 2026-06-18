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

    /**
     * 物理清理超过保留期的云端作品（按更新时间，默认 3 个月）
     *
     * @param retainMonths 保留月数
     * @return 删除条数
     */
    int cleanExpiredFiles(int retainMonths);
}
