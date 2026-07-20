package org.ruoyi.service.usercenter.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.dto.request.usercenter.WorkFileQueryRequest;
import org.ruoyi.domain.dto.request.usercenter.WorkFileSaveRequest;
import org.ruoyi.domain.entity.usercenter.UcWorkFile;
import org.ruoyi.domain.vo.usercenter.UcWorkFileVo;
import org.ruoyi.mapper.usercenter.UcWorkFileMapper;
import org.ruoyi.service.usercenter.IUserWorkFileService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserWorkFileServiceImpl implements IUserWorkFileService {

    private static final String FILE_TYPE_SOFTWARE_DIAGRAM = "software_diagram";

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
        boolean needDiagramType = query != null
            && FILE_TYPE_SOFTWARE_DIAGRAM.equals(query.getFileType());
        if (!needDiagramType) {
            // 非软件工程图列表不拉 contentJson，避免大字段拖慢分页
            lqw.select(
                UcWorkFile::getFileId,
                UcWorkFile::getUserId,
                UcWorkFile::getFileName,
                UcWorkFile::getDescription,
                UcWorkFile::getFileType,
                UcWorkFile::getThumbnail,
                UcWorkFile::getFileSize,
                UcWorkFile::getStorageType,
                UcWorkFile::getCreateTime,
                UcWorkFile::getUpdateTime
            );
        }
        // 实体分页以便在软件工程图列表中读取 contentJson 摘要
        Page<UcWorkFile> entityPage = workFileMapper.selectPage(pageQuery.build(), lqw);
        List<UcWorkFileVo> rows = entityPage.getRecords().stream()
            .map(this::toListVo)
            .collect(Collectors.toList());
        Page<UcWorkFileVo> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(rows);
        return TableDataInfo.build(voPage);
    }

    private UcWorkFileVo toListVo(UcWorkFile file) {
        UcWorkFileVo vo = new UcWorkFileVo();
        vo.setFileId(file.getFileId());
        vo.setFileName(file.getFileName());
        vo.setDescription(file.getDescription());
        vo.setFileType(file.getFileType());
        vo.setThumbnail(file.getThumbnail());
        vo.setFileSize(file.getFileSize());
        vo.setStorageType(file.getStorageType());
        vo.setCreateTime(file.getCreateTime());
        vo.setUpdateTime(file.getUpdateTime());
        if (FILE_TYPE_SOFTWARE_DIAGRAM.equals(file.getFileType())) {
            vo.setDiagramType(extractSoftwareDiagramType(file.getContentJson()));
        }
        return vo;
    }

    /** 从软件工程图 contentJson 提取当前图类型（class/sequence/...） */
    static String extractSoftwareDiagramType(String contentJson) {
        if (StringUtils.isBlank(contentJson)) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(contentJson);
            String type = obj.getStr("diagramType");
            if (StringUtils.isNotBlank(type)) {
                return type.trim();
            }
            // 兼容旧数据：仅有 byType 时取唯一/首个键
            JSONObject byType = obj.getJSONObject("byType");
            if (byType != null && !byType.isEmpty()) {
                return byType.keySet().iterator().next();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
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
            if (request.getDescription() != null) {
                existing.setDescription(request.getDescription());
            }
            if (StringUtils.isNotBlank(request.getFileType())) {
                existing.setFileType(request.getFileType());
            }
            if (request.getThumbnail() != null) {
                existing.setThumbnail(request.getThumbnail());
            }
            if (request.getContentJson() != null) {
                existing.setContentJson(request.getContentJson());
                existing.setFileSize(size);
            }
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

    @Override
    public int cleanExpiredFiles(int retainMonths) {
        int months = retainMonths > 0 ? retainMonths : 3;
        Date cutoff = Date.from(
            LocalDateTime.now().minusMonths(months).atZone(ZoneId.systemDefault()).toInstant()
        );
        return TenantHelper.ignore(() -> workFileMapper.physicalDeleteExpired(cutoff));
    }

    private UcWorkFile requireOwned(Long userId, Long fileId) {
        UcWorkFile file = workFileMapper.selectById(fileId);
        if (file == null || !userId.equals(file.getUserId())) {
            throw new ServiceException("文件不存在或无权访问");
        }
        return file;
    }
}
