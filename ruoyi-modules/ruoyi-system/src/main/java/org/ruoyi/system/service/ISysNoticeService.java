package org.ruoyi.system.service;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.system.domain.bo.SysNoticeBo;
import org.ruoyi.system.domain.vo.SysNoticeVo;

import java.util.List;

/**
 * 公告 服务层
 *
 * @author Lion Li
 */
public interface ISysNoticeService {

    /**
     * 分页查询通知公告列表
     *
     * @param notice    查询条件
     * @param pageQuery 分页参数
     * @return 通知公告分页列表
     */
    TableDataInfo<SysNoticeVo> selectPageNoticeList(SysNoticeBo notice, PageQuery pageQuery);

    /**
     * 查询公告信息
     *
     * @param noticeId 公告ID
     * @return 公告信息
     */
    SysNoticeVo selectNoticeById(Long noticeId);

    /**
     * 查询公告列表
     *
     * @param notice 公告信息
     * @return 公告集合
     */
    List<SysNoticeVo> selectNoticeList(SysNoticeBo notice);

    /**
     * 新增公告
     *
     * @param bo 公告信息
     * @return 结果
     */
    int insertNotice(SysNoticeBo bo);

    /**
     * 修改公告
     *
     * @param bo 公告信息
     * @return 结果
     */
    int updateNotice(SysNoticeBo bo);

    /**
     * 删除公告信息
     *
     * @param noticeId 公告ID
     * @return 结果
     */
    int deleteNoticeById(Long noticeId);

    /**
     * 批量删除公告信息
     *
     * @param noticeIds 需要删除的公告ID
     * @return 结果
     */
    int deleteNoticeByIds(Long[] noticeIds);

    /**
     * C 端分页查询已发布的通知公告（status=0）
     *
     * @param noticeType 公告类型：1通知 2公告，空则全部
     */
    TableDataInfo<SysNoticeVo> selectPublicPageList(String noticeType, PageQuery pageQuery);

    /**
     * C 端查询已发布的通知公告详情
     */
    SysNoticeVo selectPublicNoticeById(Long noticeId);

    /**
     * C 端获取最新一条已发布的通知公告
     */
    SysNoticeVo selectLatestPublicNotice();
}
