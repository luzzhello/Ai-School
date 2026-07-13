package org.ruoyi.service.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.PaperTemplateProperties;
import org.ruoyi.domain.paper.PaperTemplateInfo;
import org.ruoyi.domain.paper.PaperTemplateStyleMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 论文 Word 模板管理：持久化 docx、解压 unpacked（styles/numbering 等）、解析样式映射。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTemplateService {

    private static final String CLASSPATH_TEMPLATE = "paper/thesis-template.docx";
    private static final String DOCX_FILENAME = "thesis-template.docx";
    private static final String META_FILENAME = "meta.json";
    private static final String UNPACKED_DIR = "unpacked";
    private static final long MAX_TEMPLATE_BYTES = 20L * 1024 * 1024;

    private final PaperTemplateProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${sys.upload.path:./upload}")
    private String sysUploadPath;

    private volatile PaperTemplateStyleMapping cachedStyles = PaperTemplateStyleMapping.defaults();
    private volatile PaperTemplateInfo cachedInfo;

    @PostConstruct
    public void init() {
        try {
            ensureReady();
        } catch (Exception e) {
            log.error("初始化论文模板失败: {}", e.getMessage(), e);
        }
    }

    public PaperTemplateInfo getInfo() {
        ensureReady();
        if (cachedInfo != null) {
            return cachedInfo;
        }
        return loadInfoFromDisk();
    }

    public PaperTemplateStyleMapping getStyleMapping() {
        ensureReady();
        return cachedStyles;
    }

    public InputStream openTemplateInputStream() {
        ensureReady();
        try {
            return Files.newInputStream(docxPath());
        } catch (IOException e) {
            throw new ServiceException("读取论文模板失败: " + e.getMessage());
        }
    }

    public byte[] readTemplateBytes() {
        try {
            return Files.readAllBytes(docxPath());
        } catch (IOException e) {
            throw new ServiceException("读取论文模板失败: " + e.getMessage());
        }
    }

    public Path unpackedDir() {
        return baseDir().resolve(UNPACKED_DIR);
    }

    /**
     * 上传并替换模板：保存 docx → 清空并解压 unpacked → 解析 styles → 写入 meta。
     */
    public PaperTemplateInfo upload(MultipartFile file) {
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

        try {
            Files.createDirectories(baseDir());
            Path docx = docxPath();
            byte[] bytes = file.getBytes();
            Files.write(docx, bytes);

            refreshUnpacked(docx);
            PaperTemplateStyleMapping styles = parseStyleMapping(unpackedDir());
            cachedStyles = styles;

            PaperTemplateInfo info = PaperTemplateInfo.builder()
                .originalFilename(filename)
                .fileSize(Files.size(docx))
                .updatedAt(System.currentTimeMillis())
                .headingCount(countHeadings(docx))
                .styles(styles)
                .docxPath(docx.toString())
                .unpackedPath(unpackedDir().toString())
                .build();
            writeMeta(info);
            cachedInfo = info;
            log.info("论文模板已更新: {}, headings={}, styles={}", filename, info.getHeadingCount(), styles);
            return info;
        } catch (IOException e) {
            throw new ServiceException("保存论文模板失败: " + e.getMessage());
        }
    }

    /**
     * 恢复为 classpath 内置默认模板。
     */
    public PaperTemplateInfo resetToDefault() {
        try {
            Files.createDirectories(baseDir());
            copyClasspathTemplate(docxPath());
            refreshUnpacked(docxPath());

            PaperTemplateStyleMapping styles = parseStyleMapping(unpackedDir());
            cachedStyles = styles;

            PaperTemplateInfo info = PaperTemplateInfo.builder()
                .originalFilename("thesis-template.docx (built-in)")
                .fileSize(Files.size(docxPath()))
                .updatedAt(System.currentTimeMillis())
                .headingCount(countHeadings(docxPath()))
                .styles(styles)
                .docxPath(docxPath().toString())
                .unpackedPath(unpackedDir().toString())
                .build();
            writeMeta(info);
            cachedInfo = info;
            log.info("论文模板已恢复为内置默认");
            return info;
        } catch (IOException e) {
            throw new ServiceException("恢复默认模板失败: " + e.getMessage());
        }
    }

    private void ensureReady() {
        try {
            Path docx = docxPath();
            Path unpacked = unpackedDir();
            if (!Files.isRegularFile(docx)) {
                copyClasspathTemplate(docx);
            }
            if (!Files.isRegularFile(unpacked.resolve("word/styles.xml"))) {
                refreshUnpacked(docx);
            }
            if (cachedInfo == null) {
                cachedInfo = loadInfoFromDisk();
            }
            if (cachedInfo != null && cachedInfo.getStyles() != null) {
                cachedStyles = cachedInfo.getStyles();
            } else {
                cachedStyles = parseStyleMapping(unpacked);
            }
        } catch (IOException e) {
            throw new ServiceException("论文模板未就绪: " + e.getMessage());
        }
    }

    private PaperTemplateInfo loadInfoFromDisk() {
        Path meta = metaPath();
        if (Files.isRegularFile(meta)) {
            try {
                PaperTemplateInfo info = objectMapper.readValue(meta.toFile(), PaperTemplateInfo.class);
                if (info.getStyles() != null) {
                    cachedStyles = info.getStyles();
                }
                return info;
            } catch (IOException e) {
                log.warn("读取模板 meta.json 失败: {}", e.getMessage());
            }
        }
        try {
            Path docx = docxPath();
            if (!Files.isRegularFile(docx)) {
                return null;
            }
            PaperTemplateStyleMapping styles = parseStyleMapping(unpackedDir());
            cachedStyles = styles;
            PaperTemplateInfo info = PaperTemplateInfo.builder()
                .originalFilename(DOCX_FILENAME)
                .fileSize(Files.size(docx))
                .updatedAt(Files.getLastModifiedTime(docx).toMillis())
                .headingCount(countHeadings(docx))
                .styles(styles)
                .docxPath(docx.toString())
                .unpackedPath(unpackedDir().toString())
                .build();
            writeMeta(info);
            return info;
        } catch (IOException e) {
            log.warn("构建模板信息失败: {}", e.getMessage());
            return null;
        }
    }

    private void writeMeta(PaperTemplateInfo info) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(metaPath().toFile(), info);
    }

    private void refreshUnpacked(Path docx) throws IOException {
        Path unpacked = unpackedDir();
        deleteDirectory(unpacked);
        Files.createDirectories(unpacked);
        unzip(docx, unpacked);
    }

    private void validateDocx(MultipartFile file) {
        try (InputStream in = file.getInputStream(); XWPFDocument ignored = new XWPFDocument(in)) {
            // ok
        } catch (Exception e) {
            throw new ServiceException("无效的 Word 模板: " + e.getMessage());
        }
    }

    private int countHeadings(Path docx) {
        try (InputStream in = Files.newInputStream(docx); XWPFDocument doc = new XWPFDocument(in)) {
            PaperTemplateStyleMapping mapping = cachedStyles != null ? cachedStyles : parseStyleMapping(unpackedDir());
            int count = 0;
            for (var paragraph : doc.getParagraphs()) {
                String styleId = paragraph.getStyleID();
                if (styleId != null && mapping.headingStyleIds().contains(styleId)) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("统计模板标题数失败: {}", e.getMessage());
            return 0;
        }
    }

    PaperTemplateStyleMapping parseStyleMapping(Path unpacked) {
        Path stylesXml = unpacked.resolve("word/styles.xml");
        if (!Files.isRegularFile(stylesXml)) {
            return PaperTemplateStyleMapping.defaults();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(stylesXml.toFile());
            Map<String, String> nameToId = new HashMap<>();
            NodeList styles = document.getElementsByTagNameNS("*", "style");
            for (int i = 0; i < styles.getLength(); i++) {
                Element style = (Element) styles.item(i);
                String styleId = style.getAttribute("w:styleId");
                if (StringUtils.isBlank(styleId)) {
                    styleId = style.getAttribute("styleId");
                }
                NodeList names = style.getElementsByTagNameNS("*", "name");
                if (names.getLength() == 0 || StringUtils.isBlank(styleId)) {
                    continue;
                }
                Element nameEl = (Element) names.item(0);
                String nameVal = nameEl.getAttribute("w:val");
                if (StringUtils.isBlank(nameVal)) {
                    nameVal = nameEl.getAttribute("val");
                }
                if (StringUtils.isNotBlank(nameVal)) {
                    nameToId.put(normalizeStyleName(nameVal), styleId);
                }
            }
            return PaperTemplateStyleMapping.builder()
                .normal(firstId(nameToId, "normal", "1"))
                .heading1(firstId(nameToId, "heading 1", "2"))
                .heading2(firstId(nameToId, "heading 2", "3"))
                .heading3(firstId(nameToId, "heading 3", "4"))
                .toc1(firstId(nameToId, "toc 1", "7"))
                .toc2(firstId(nameToId, "toc 2", "8"))
                .toc3(firstId(nameToId, "toc 3", "6"))
                .reference(firstId(nameToId, "参考文献", "13"))
                .build();
        } catch (Exception e) {
            log.warn("解析模板 styles.xml 失败，使用默认样式映射: {}", e.getMessage());
            return PaperTemplateStyleMapping.defaults();
        }
    }

    private String firstId(Map<String, String> nameToId, String primaryName, String fallback) {
        String id = nameToId.get(normalizeStyleName(primaryName));
        return StringUtils.isNotBlank(id) ? id : fallback;
    }

    private String normalizeStyleName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
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

    private void unzip(Path docx, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir.normalize())) {
                    throw new ServiceException("非法压缩条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new ServiceException("清理模板目录失败: " + e.getMessage());
                }
            });
        }
    }

    private Path baseDir() {
        String configured = properties.getLocalDir();
        String base = StringUtils.isNotBlank(configured)
            ? configured
            : Paths.get(sysUploadPath, "paper", "template").toString();
        return Paths.get(base).toAbsolutePath().normalize();
    }

    private Path docxPath() {
        return baseDir().resolve(DOCX_FILENAME);
    }

    private Path metaPath() {
        return baseDir().resolve(META_FILENAME);
    }
}
