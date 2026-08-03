package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 会话自定义排版（上传 docx + 主 JSON）管理。
 *
 * <p>自定义模式下有效配置合并顺序：内置默认 ← 自定义主 JSON ← 会话覆盖 JSON。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperSessionCustomFormatService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final String DOCX_NAME = "thesis-template.docx";

    private final PaperFormatTemplateService paperFormatTemplateService;

    @Value("${sys.upload.path:./upload}")
    private String sysUploadPath;

    /** 会话是否处于自定义排版模式：{@code customFormatDocxPath} 非空即为自定义模式。 */
    public static boolean isCustomMode(PaperSession session) {
        return session != null && StringUtils.isNotBlank(session.getCustomFormatDocxPath());
    }

    /**
     * 根据 {@code customPatchStyles} 设置有效配置的 {@code export.patchTemplateStyles}。
     * {@code null} 或非 0 视为需要 patch（自定义模式默认 patch）。
     */
    public static void applyPatchFlag(PaperFormatConfig effective, Integer customPatchStyles) {
        if (effective.getExport() == null) {
            effective.setExport(new PaperFormatConfig.Export());
        }
        boolean patch = customPatchStyles == null || customPatchStyles != 0;
        effective.getExport().setPatchTemplateStyles(patch);
    }

    /** 清空会话上的自定义排版字段，回退到模板模式。 */
    public static void clearCustomFields(PaperSession session) {
        session.setCustomFormatDocxPath(null);
        session.setCustomFormatDocxName(null);
        session.setCustomFormatDocxSize(null);
        session.setCustomFormatJson(null);
        session.setCustomPatchStyles(null);
    }

    /**
     * 保存会话自定义排版 docx + 主 JSON。仅修改传入的 {@code session} 对象字段，持久化由调用方负责。
     *
     * @param formatOrNull 为空时使用当前生效配置（模板/自定义 + 覆盖）作为快照；非空时与内置默认 merge 后校验
     */
    public void saveCustomDocx(PaperSession session, Long userId, MultipartFile file,
            PaperFormatConfig formatOrNull, Boolean patchStyles) {
        if (session == null || StringUtils.isBlank(session.getSessionId())) {
            throw new ServiceException("会话不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("模板文件不能为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ServiceException("模板文件不能超过 10MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new ServiceException("仅支持 .docx 格式的 Word 模板");
        }
        validateDocx(file);

        // 快照必须在写入自定义字段之前计算：否则 formatOrNull==null 时会用尚未更新的旧状态，
        // 而 resolveEffective(session) 会根据当前 session 是否已处于自定义模式自动选择正确的合并链路。
        String json;
        if (formatOrNull == null) {
            PaperFormatConfig snapshot = paperFormatTemplateService.resolveEffective(session);
            json = PaperFormatMerger.toJson(snapshot);
        } else {
            try {
                PaperFormatConfig effective = PaperFormatMerger.merge(PaperFormatDefaults.dalianOcean(), formatOrNull);
                PaperFormatMerger.validate(effective);
                json = PaperFormatMerger.toJson(effective);
            } catch (IllegalArgumentException e) {
                throw new ServiceException(e.getMessage());
            }
        }

        Path target = absoluteDocxPath(userId, session.getSessionId());
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, file.getBytes());
            long size = Files.size(target);

            session.setCustomFormatDocxPath(relativeDocxPath(userId, session.getSessionId()));
            session.setCustomFormatDocxName(filename);
            session.setCustomFormatDocxSize(size);
            session.setCustomFormatJson(json);
            session.setCustomPatchStyles(Boolean.FALSE.equals(patchStyles) ? 0 : 1);
            session.setFormatTemplateId(null);
            log.info("会话自定义排版 docx 已保存: sessionId={}, userId={}, file={}, size={}",
                session.getSessionId(), userId, filename, size);
        } catch (IOException e) {
            throw new ServiceException("保存自定义排版模板失败: " + e.getMessage());
        }
    }

    /** 清除会话自定义排版：best-effort 删除磁盘文件，始终清空会话字段。 */
    public void clearCustomDocx(PaperSession session, Long userId) {
        if (session != null && StringUtils.isNotBlank(session.getSessionId())) {
            Path dir = absoluteDocxPath(userId, session.getSessionId()).getParent();
            try {
                deleteRecursively(dir);
            } catch (IOException e) {
                log.warn("删除会话自定义排版目录失败: userId={}, sessionId={}, error={}",
                    userId, session.getSessionId(), e.getMessage());
            }
        }
        if (session != null) {
            clearCustomFields(session);
        }
    }

    /** 打开会话自定义排版 docx；文件缺失时抛出业务异常（不回退内置模板）。 */
    public InputStream openCustomDocx(PaperSession session) {
        if (session == null || StringUtils.isBlank(session.getCustomFormatDocxPath())) {
            throw new ServiceException("自定义排版模板文件缺失，请重新上传");
        }
        Path root = uploadRoot();
        Path path = root.resolve(session.getCustomFormatDocxPath().replace('\\', '/')).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new ServiceException("自定义排版模板文件缺失，请重新上传");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new ServiceException("读取自定义排版模板失败: " + e.getMessage());
        }
    }

    private Path uploadRoot() {
        return Paths.get(sysUploadPath).toAbsolutePath().normalize();
    }

    /** 会话自定义排版 docx 的绝对路径：{@code {uploadRoot}/paper/session-format/{userId}/{sessionId}/thesis-template.docx} */
    public Path absoluteDocxPath(Long userId, String sessionId) {
        return uploadRoot().resolve("paper").resolve("session-format")
            .resolve(String.valueOf(userId)).resolve(sessionId).resolve(DOCX_NAME);
    }

    /** 相对 uploadRoot 的路径，例如 {@code paper/session-format/1/abc-123/thesis-template.docx} */
    public String relativeDocxPath(Long userId, String sessionId) {
        return "paper/session-format/" + userId + "/" + sessionId + "/" + DOCX_NAME;
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("删除文件失败: {}, error={}", path, e.getMessage());
                }
            });
        }
    }

    private void validateDocx(MultipartFile file) {
        try (InputStream in = file.getInputStream(); XWPFDocument ignored = new XWPFDocument(in)) {
            // ok
        } catch (Exception e) {
            throw new ServiceException("无效的 Word 模板: " + e.getMessage());
        }
    }
}
