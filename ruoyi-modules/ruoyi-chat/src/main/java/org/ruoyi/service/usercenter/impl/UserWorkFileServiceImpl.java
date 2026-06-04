package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.usercenter.WorkFileQueryRequest;
import org.ruoyi.domain.dto.request.usercenter.WorkFileSaveRequest;
import org.ruoyi.domain.entity.usercenter.UcWorkFile;
import org.ruoyi.domain.vo.usercenter.UcWorkFileVo;
import org.ruoyi.mapper.usercenter.UcWorkFileMapper;
import org.ruoyi.service.usercenter.IUserWorkFileService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserWorkFileServiceImpl implements IUserWorkFileService {

    private final UcWorkFileMapper workFileMapper;

    @Override
    public TableDataInfo<UcWorkFileVo> page(Long userId, WorkFileQueryRequest query, PageQuery pageQuery) {
        LambdaQueryWrapper<UcWorkFile> lqw = Wrappers.lambdaQuery();
        lqw.eq(UcWorkFile::getUserId, userId);
        if (query != null) {
            lqw.like(StringUtils.isNotBlank(query.getFileName()), UcWorkFile::getFileName, query.getFileName());
            lqw.eq(StringUtils.isNotBlank(query.getFileType()), UcWorkFile::getFileType, query.getFileType());
        }
        lqw.orderByDesc(UcWorkFile::getUpdateTime);
        Page<UcWorkFileVo> page = workFileMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public UcWorkFileVo detail(Long userId, Long fileId) {
        UcWorkFile file = requireOwned(userId, fileId);
        return workFileMapper.selectVoById(file.getFileId());
    }

    @Override
    public Long save(Long userId, WorkFileSaveRequest request) {
        long size = request.getContentJson() != null ? request.getContentJson().length() : 0L;
        if (request.getFileId() != null) {
            UcWorkFile existing = requireOwned(userId, request.getFileId());
            existing.setFileName(request.getFileName());
            existing.setDescription(request.getDescription());
            existing.setFileType(request.getFileType());
            existing.setThumbnail(request.getThumbnail());
            existing.setContentJson(request.getContentJson());
            existing.setFileSize(size);
            workFileMapper.updateById(existing);
            return existing.getFileId();
        }
        UcWorkFile file = new UcWorkFile();
        file.setUserId(userId);
        file.setFileName(request.getFileName());
        file.setDescription(request.getDescription());
        file.setFileType(request.getFileType());
        file.setThumbnail(request.getThumbnail());
        file.setContentJson(request.getContentJson());
        file.setFileSize(size);
        file.setStorageType("cloud");
        file.setTenantId(LoginHelper.getTenantId());
        workFileMapper.insert(file);
        return file.getFileId();
    }

    @Override
    public void remove(Long userId, Long fileId) {
        requireOwned(userId, fileId);
        workFileMapper.deleteById(fileId);
    }

    private UcWorkFile requireOwned(Long userId, Long fileId) {
        UcWorkFile file = workFileMapper.selectById(fileId);
        if (file == null || !userId.equals(file.getUserId())) {
            throw new ServiceException("文件不存在或无权访问");
        }
        return file;
    }
}
