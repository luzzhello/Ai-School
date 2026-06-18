package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.usercenter.UcWorkFileBo;
import org.ruoyi.domain.entity.usercenter.UcWorkFile;
import org.ruoyi.domain.vo.usercenter.UcWorkFileAdminVo;
import org.ruoyi.mapper.usercenter.UcWorkFileMapper;
import org.ruoyi.service.usercenter.IAdminWorkFileService;
import org.ruoyi.service.usercenter.UserCenterAdminHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AdminWorkFileServiceImpl implements IAdminWorkFileService {

    private final UcWorkFileMapper workFileMapper;
    private final UserCenterAdminHelper adminHelper;

    @Override
    public TableDataInfo<UcWorkFileAdminVo> queryPageList(UcWorkFileBo bo, PageQuery pageQuery) {
        return TenantHelper.ignore(() -> {
            LambdaQueryWrapper<UcWorkFile> lqw = buildQueryWrapper(bo);
            Page<UcWorkFile> page = workFileMapper.selectPage(pageQuery.build(), lqw);
            List<UcWorkFileAdminVo> rows = page.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());
            Page<UcWorkFileAdminVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            voPage.setRecords(rows);
            return TableDataInfo.build(voPage);
        });
    }

    private LambdaQueryWrapper<UcWorkFile> buildQueryWrapper(UcWorkFileBo bo) {
        LambdaQueryWrapper<UcWorkFile> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getUserId() != null, UcWorkFile::getUserId, bo.getUserId());
            lqw.like(StringUtils.isNotBlank(bo.getFileName()), UcWorkFile::getFileName, bo.getFileName());
            lqw.eq(StringUtils.isNotBlank(bo.getFileType()), UcWorkFile::getFileType, bo.getFileType());
            adminHelper.applyCreateTimeRange(lqw, UcWorkFile::getCreateTime, bo);
        }
        lqw.orderByDesc(UcWorkFile::getUpdateTime);
        return lqw;
    }

    private UcWorkFileAdminVo toVo(UcWorkFile file) {
        UcWorkFileAdminVo vo = new UcWorkFileAdminVo();
        vo.setFileId(file.getFileId());
        vo.setUserId(file.getUserId());
        vo.setUserName(adminHelper.resolveDisplayName(file.getUserId()));
        vo.setFileName(file.getFileName());
        vo.setDescription(file.getDescription());
        vo.setFileType(file.getFileType());
        vo.setThumbnail(file.getThumbnail());
        vo.setFileSize(file.getFileSize());
        vo.setStorageType(file.getStorageType());
        vo.setCreateTime(file.getCreateTime());
        vo.setUpdateTime(file.getUpdateTime());
        return vo;
    }
}
