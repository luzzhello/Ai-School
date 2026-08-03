package org.ruoyi.service.paper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.PaperFormatTemplateProperties;
import org.ruoyi.domain.entity.paper.PaperFormatTemplateEntity;
import org.ruoyi.domain.entity.paper.PaperSessionEntity;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;
import org.ruoyi.mapper.paper.PaperFormatTemplateMapper;
import org.ruoyi.mapper.paper.PaperSessionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 论文排版模板：元数据 / format_json / docx 管理与有效配置解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperFormatTemplateService {

    private static final String CLASSPATH_TEMPLATE = "paper/thesis-template.docx";
    private static final String DOCX_FILENAME = "thesis-template.docx";
    private static final long MAX_TEMPLATE_BYTES = 20L * 1024 * 1024;
    private static final String STATUS_ENABLED = "1";
    private static final String STATUS_DISABLED = "0";

    private final PaperFormatTemplateMapper templateMapper;
    private final PaperSessionMapper sessionMapper;
    private final PaperFormatTemplateProperties properties;

    @Value("${sys.upload.path:./upload}")
    private String sysUploadPath;

    @PostConstruct
    public void init() {
        try {
            ensureDefaultDocxReady();
        } catch (Exception e) {
            log.error("初始化默认排版模板 docx 失败: {}", e.getMessage(), e);
        }
    }

    public List<PaperFormatTemplateEntity> listEnabled() {
        return templateMapper.selectList(Wrappers.<PaperFormatTemplateEntity>lambdaQuery()
            .eq(PaperFormatTemplateEntity::getStatus, STATUS_ENABLED)
            .orderByDesc(PaperFormatTemplateEntity::getIsDefault)
            .orderByAsc(PaperFormatTemplateEntity::getId));
    }

    public List<PaperFormatTemplateEntity> listAll() {
        return templateMapper.selectList(Wrappers.<PaperFormatTemplateEntity>lambdaQuery()
            .orderByDesc(PaperFormatTemplateEntity::getIsDefault)
            .orderByAsc(PaperFormatTemplateEntity::getId));
    }

    public PaperFormatTemplateEntity getById(Long id) {
        if (id == null) {
            return null;
        }
        return templateMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(PaperFormatTemplateEntity meta, PaperFormatConfig format) {
        if (meta == null) {
            throw new ServiceException("模板信息不能为空");
        }
        if (StringUtils.isBlank(meta.getName())) {
            throw new ServiceException("模板名称不能为空");
        }
        PaperFormatConfig overlay = format != null ? format : new PaperFormatConfig();
        validateMergedOverlay(overlay);

        Date now = new Date();
        PaperFormatTemplateEntity entity = new PaperFormatTemplateEntity();
        entity.setName(meta.getName().trim());
        entity.setSchoolName(meta.getSchoolName());
        entity.setRemark(meta.getRemark());
        entity.setStyleMappingJson(meta.getStyleMappingJson());
        entity.setStatus(StringUtils.isNotBlank(meta.getStatus()) ? meta.getStatus() : STATUS_ENABLED);
        if (!STATUS_ENABLED.equals(entity.getStatus()) && !STATUS_DISABLED.equals(entity.getStatus())) {
            throw new ServiceException("状态仅支持 0 或 1");
        }
        boolean makeDefault = meta.getIsDefault() != null && meta.getIsDefault() == 1;
        entity.setIsDefault(makeDefault ? 1 : 0);
        entity.setFormatJson(PaperFormatMerger.toJson(overlay));
        entity.setCreateBy(meta.getCreateBy());
        entity.setUpdateBy(meta.getUpdateBy());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        if (makeDefault) {
            clearDefaultFlags();
        }
        templateMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMetaAndFormat(Long id, PaperFormatTemplateEntity meta, PaperFormatConfig format) {
        PaperFormatTemplateEntity existing = requireById(id);
        if (meta == null) {
            throw new ServiceException("模板信息不能为空");
        }
        if (format != null) {
            validateMergedOverlay(format);
            existing.setFormatJson(PaperFormatMerger.toJson(format));
        }

        if (StringUtils.isNotBlank(meta.getName())) {
            existing.setName(meta.getName().trim());
        }
        if (meta.getSchoolName() != null) {
            existing.setSchoolName(meta.getSchoolName());
        }
        if (meta.getRemark() != null) {
            existing.setRemark(meta.getRemark());
        }
        if (meta.getStyleMappingJson() != null) {
            existing.setStyleMappingJson(meta.getStyleMappingJson());
        }
        if (StringUtils.isNotBlank(meta.getStatus())) {
            if (!STATUS_ENABLED.equals(meta.getStatus()) && !STATUS_DISABLED.equals(meta.getStatus())) {
                throw new ServiceException("状态仅支持 0 或 1");
            }
            existing.setStatus(meta.getStatus());
        }
        if (meta.getUpdateBy() != null) {
            existing.setUpdateBy(meta.getUpdateBy());
        }
        existing.setUpdateTime(new Date());
        templateMapper.updateById(existing);
    }

    public void uploadDocx(Long id, MultipartFile file) {
        PaperFormatTemplateEntity existing = requireById(id);
        if (file == null || file.isEmpty()) {
            throw new ServiceException("模板文件不能为空");
        }
        if (file.getSize() > MAX_TEMPLATE_BYTES) {
            throw new ServiceException("模板文件不能超过 20MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new ServiceException("仅支持 .docx 格式的 Word 模板");
        }
        validateDocx(file);

        Path target = templateDocxPath(id);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, file.getBytes());
            long size = Files.size(target);
            String relative = relativeDocxPath(id);

            existing.setDocxPath(relative);
            existing.setDocxOriginalName(filename);
            existing.setDocxSize(size);
            existing.setUpdateTime(new Date());
            templateMapper.updateById(existing);
            log.info("排版模板 docx 已更新: id={}, file={}, size={}", id, filename, size);
        } catch (IOException e) {
            throw new ServiceException("保存排版模板失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        PaperFormatTemplateEntity entity = requireById(id);
        if (!STATUS_ENABLED.equals(entity.getStatus())) {
            throw new ServiceException("停用模板不能设为默认");
        }
        clearDefaultFlags();
        PaperFormatTemplateEntity update = new PaperFormatTemplateEntity();
        update.setId(id);
        update.setIsDefault(1);
        update.setUpdateTime(new Date());
        templateMapper.updateById(update);
    }

    public void setStatus(Long id, String status) {
        requireById(id);
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new ServiceException("状态仅支持 0 或 1");
        }
        PaperFormatTemplateEntity update = new PaperFormatTemplateEntity();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(new Date());
        templateMapper.updateById(update);
    }

    /**
     * 打开模板 docx。{@code id == null} 时使用默认模板；磁盘缺失时回退 classpath 内置模板。
     */
    public InputStream openDocx(Long id) {
        PaperFormatTemplateEntity entity = id != null ? getById(id) : findDefault();
        if (entity != null) {
            Path disk = resolveStoredDocx(entity);
            if (disk != null && Files.isRegularFile(disk)) {
                try {
                    return Files.newInputStream(disk);
                } catch (IOException e) {
                    log.warn("读取排版模板 docx 失败 id={}: {}", entity.getId(), e.getMessage());
                }
            }
        }
        return openClasspathTemplate();
    }

    /**
     * 合并：内置默认 ← 模板 format_json ← 会话 overrideJson。
     */
    public PaperFormatConfig resolveEffective(Long templateId, String overrideJson) {
        try {
            PaperFormatConfig def = PaperFormatDefaults.dalianOcean();
            PaperFormatConfig templateOverlay = new PaperFormatConfig();
            if (templateId != null) {
                PaperFormatTemplateEntity entity = getById(templateId);
                if (entity != null) {
                    templateOverlay = PaperFormatMerger.parseJson(entity.getFormatJson());
                }
            } else {
                PaperFormatTemplateEntity entity = findDefault();
                if (entity != null) {
                    templateOverlay = PaperFormatMerger.parseJson(entity.getFormatJson());
                }
            }
            PaperFormatConfig sessionOverlay = PaperFormatMerger.parseJson(overrideJson);
            PaperFormatConfig effective = PaperFormatMerger.merge(def, templateOverlay, sessionOverlay);
            PaperFormatMerger.validate(effective);
            return effective;
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 合并：内置默认 ← 模板/自定义 ← 会话 overrideJson。
     *
     * <p>自定义模式（{@code customFormatDocxPath} 非空）：内置默认 ← 自定义主 JSON ← 会话覆盖 JSON，
     * 并按 {@code customPatchStyles} 设置 export.patchTemplateStyles。否则回退到模板模式。</p>
     */
    public PaperFormatConfig resolveEffective(PaperSession session) {
        if (session == null) {
            return resolveEffective(null, null);
        }
        if (PaperSessionCustomFormatService.isCustomMode(session)) {
            try {
                PaperFormatConfig def = PaperFormatDefaults.dalianOcean();
                PaperFormatConfig custom = PaperFormatMerger.parseJson(session.getCustomFormatJson());
                PaperFormatConfig override = PaperFormatMerger.parseJson(session.getFormatOverrideJson());
                PaperFormatConfig effective = PaperFormatMerger.merge(def, custom, override);
                PaperSessionCustomFormatService.applyPatchFlag(effective, session.getCustomPatchStyles());
                PaperFormatMerger.validate(effective);
                return effective;
            } catch (IllegalArgumentException e) {
                throw new ServiceException(e.getMessage());
            }
        }
        return resolveEffective(session.getFormatTemplateId(), session.getFormatOverrideJson());
    }

    public boolean isReferencedBySession(Long id) {
        if (id == null) {
            return false;
        }
        Long count = sessionMapper.selectCount(Wrappers.<PaperSessionEntity>lambdaQuery()
            .eq(PaperSessionEntity::getFormatTemplateId, id));
        return count != null && count > 0;
    }

    public PaperFormatTemplateEntity findDefault() {
        return templateMapper.selectOne(Wrappers.<PaperFormatTemplateEntity>lambdaQuery()
            .eq(PaperFormatTemplateEntity::getIsDefault, 1)
            .last("LIMIT 1"));
    }

    private void ensureDefaultDocxReady() throws IOException {
        PaperFormatTemplateEntity def = findDefault();
        if (def == null || def.getId() == null) {
            return;
        }
        Path target = templateDocxPath(def.getId());
        boolean missingOnDisk = !Files.isRegularFile(target);
        boolean pathBlank = StringUtils.isBlank(def.getDocxPath());
        if (!pathBlank && !missingOnDisk) {
            Path stored = resolveStoredDocx(def);
            if (stored != null && Files.isRegularFile(stored)) {
                return;
            }
            missingOnDisk = true;
        }
        if (pathBlank || missingOnDisk) {
            copyClasspathTemplate(target);
            long size = Files.size(target);
            def.setDocxPath(relativeDocxPath(def.getId()));
            if (StringUtils.isBlank(def.getDocxOriginalName())) {
                def.setDocxOriginalName(DOCX_FILENAME);
            }
            def.setDocxSize(size);
            def.setUpdateTime(new Date());
            templateMapper.updateById(def);
            log.info("已从 classpath 初始化默认排版模板 docx: id={}, path={}", def.getId(), def.getDocxPath());
        }
    }

    private PaperFormatTemplateEntity requireById(Long id) {
        PaperFormatTemplateEntity entity = getById(id);
        if (entity == null) {
            throw new ServiceException("排版模板不存在: " + id);
        }
        return entity;
    }

    private void validateMergedOverlay(PaperFormatConfig overlay) {
        try {
            PaperFormatConfig effective = PaperFormatMerger.merge(PaperFormatDefaults.dalianOcean(), overlay);
            PaperFormatMerger.validate(effective);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    private void clearDefaultFlags() {
        templateMapper.update(null, new LambdaUpdateWrapper<PaperFormatTemplateEntity>()
            .set(PaperFormatTemplateEntity::getIsDefault, 0)
            .eq(PaperFormatTemplateEntity::getIsDefault, 1));
    }

    private Path baseDir() {
        String configured = properties.getLocalDir();
        String base = StringUtils.isNotBlank(configured)
            ? configured
            : Paths.get(sysUploadPath, "paper", "format-templates").toString();
        return Paths.get(base).toAbsolutePath().normalize();
    }

    private Path templateDocxPath(Long id) {
        return baseDir().resolve(String.valueOf(id)).resolve(DOCX_FILENAME);
    }

    /** 相对 localDir 的路径，例如 {@code 1/thesis-template.docx} */
    private String relativeDocxPath(Long id) {
        return id + "/" + DOCX_FILENAME;
    }

    private Path resolveStoredDocx(PaperFormatTemplateEntity entity) {
        if (entity == null) {
            return null;
        }
        String stored = entity.getDocxPath();
        if (StringUtils.isNotBlank(stored)) {
            Path asAbsolute = Paths.get(stored);
            if (asAbsolute.isAbsolute() && Files.isRegularFile(asAbsolute)) {
                return asAbsolute.normalize();
            }
            Path underBase = baseDir().resolve(stored.replace('\\', '/')).normalize();
            if (underBase.startsWith(baseDir()) && Files.isRegularFile(underBase)) {
                return underBase;
            }
        }
        if (entity.getId() != null) {
            Path canonical = templateDocxPath(entity.getId());
            if (Files.isRegularFile(canonical)) {
                return canonical;
            }
        }
        return null;
    }

    private void validateDocx(MultipartFile file) {
        try (InputStream in = file.getInputStream(); XWPFDocument ignored = new XWPFDocument(in)) {
            // ok
        } catch (Exception e) {
            throw new ServiceException("无效的 Word 模板: " + e.getMessage());
        }
    }

    private void copyClasspathTemplate(Path target) throws IOException {
        ClassPathResource resource = new ClassPathResource(CLASSPATH_TEMPLATE);
        if (!resource.exists()) {
            throw new ServiceException("内置论文模板不存在: " + CLASSPATH_TEMPLATE);
        }
        Files.createDirectories(target.getParent());
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private InputStream openClasspathTemplate() {
        ClassPathResource resource = new ClassPathResource(CLASSPATH_TEMPLATE);
        if (!resource.exists()) {
            throw new ServiceException("内置论文模板不存在: " + CLASSPATH_TEMPLATE);
        }
        try {
            return resource.getInputStream();
        } catch (IOException e) {
            throw new ServiceException("读取内置论文模板失败: " + e.getMessage());
        }
    }
}
