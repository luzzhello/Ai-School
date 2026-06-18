package org.ruoyi.service.draw.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.CourseCodeProperties;
import org.ruoyi.domain.dto.request.CourseCodeGenerateRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
class CourseCodeZipBuilder {

    private static final String CLASSPATH_ZIP = "course-code/template.zip";
    private static final Set<String> SKIP_DIR_NAMES = Set.of(".git", "node_modules", "target", ".idea");
    private static final Set<String> SKIP_FILE_NAMES = Set.of("package-lock.json");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
        ".java", ".xml", ".yml", ".yaml", ".properties", ".sql", ".md", ".ts", ".tsx",
        ".js", ".json", ".less", ".css", ".html", ".d.ts", ".config", ".editorconfig"
    );

    private final CourseCodeProperties properties;
    private final CourseCodeModuleCloner moduleCloner;

    record ZipBuildResult(byte[] bytes, List<String> files, int fileCount) {
    }

    ZipBuildResult build(
        CourseCodeGenerateRequest request,
        String projectName,
        List<SqlTableDocParser.SqlTableDef> tables) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("course-code-");
            materializeTemplate(workDir);
            customizeTemplate(workDir, request, projectName, tables);
            List<String> files = listRelativeFiles(workDir);
            byte[] bytes = zipDirectory(workDir);
            return new ZipBuildResult(bytes, files, files.size());
        }
        catch (IOException e) {
            log.error("课设代码打包失败", e);
            throw new ServiceException("课设代码打包失败，请稍后重试");
        }
        finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private void materializeTemplate(Path workDir) throws IOException {
        Path templateDir = resolveTemplateDir();
        if (templateDir != null) {
            copyDirectory(templateDir, workDir);
            return;
        }
        ClassPathResource resource = new ClassPathResource(CLASSPATH_ZIP);
        if (!resource.exists()) {
            throw new ServiceException("课设代码模板未配置，请联系管理员");
        }
        try (InputStream in = resource.getInputStream()) {
            unzip(in, workDir);
        }
    }

    private Path resolveTemplateDir() throws IOException {
        String configured = properties.getTemplateDir();
        if (StringUtils.isBlank(configured)) {
            return null;
        }
        Path dir = Path.of(configured.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        if (!Files.exists(dir.resolve("template-backend")) || !Files.exists(dir.resolve("template-frontend"))) {
            throw new ServiceException("课设模板目录缺少 template-backend 或 template-frontend");
        }
        return dir;
    }

    private void customizeTemplate(
        Path workDir,
        CourseCodeGenerateRequest request,
        String projectName,
        List<SqlTableDocParser.SqlTableDef> tables) throws IOException {
        String author = StringUtils.trim(request.getAuthor());
        String today = LocalDate.now().toString().replace('-', '/');
        replaceAuthorInTree(workDir, author, today);

        if ("sql".equalsIgnoreCase(request.getMode())) {
            Path schemaPath = workDir.resolve("template-backend/src/main/resources/schema-generated.sql");
            Files.createDirectories(schemaPath.getParent());
            Files.writeString(schemaPath, request.getContent().trim(), StandardCharsets.UTF_8);
            moduleCloner.generateFromSql(workDir, tables, author, today);
        }

        Path readme = workDir.resolve("README.md");
        if (Files.exists(readme)) {
            String text = Files.readString(readme, StandardCharsets.UTF_8);
            text = text + "\n\n---\n\n生成项目：" + projectName + "\n生成作者：" + author + "\n";
            Files.writeString(readme, text, StandardCharsets.UTF_8);
        }
    }

    private void replaceAuthorInTree(Path root, String author, String today) throws IOException {
        String safeAuthor = StringUtils.defaultIfBlank(author, "校园小助手");
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(this::isTextLike)
                .forEach(path -> {
                    try {
                        String content = Files.readString(path, StandardCharsets.UTF_8);
                        String replaced = content
                            .replace("校园小助手", safeAuthor)
                            .replace("@author 校园小助手", "@author " + safeAuthor);
                        if (!replaced.equals(content)) {
                            Files.writeString(path, replaced, StandardCharsets.UTF_8);
                        }
                    }
                    catch (IOException e) {
                        log.warn("替换作者失败: {}", path, e);
                    }
                });
        }
    }

    private boolean isTextLike(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (SKIP_FILE_NAMES.contains(name)) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return TEXT_EXTENSIONS.contains(name.substring(dot));
    }

    private List<String> listRelativeFiles(Path root) throws IOException {
        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .forEach(path -> files.add(root.relativize(path).toString().replace('\\', '/')));
        }
        return files;
    }

    private byte[] zipDirectory(Path root) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos);
             Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .forEach(path -> {
                    String entryName = root.relativize(path).toString().replace('\\', '/');
                    try {
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                    catch (IOException e) {
                        throw new ServiceException("压缩文件失败: " + entryName);
                    }
                });
        }
        return baos.toByteArray();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    if (shouldSkip(relative)) {
                        return;
                    }
                    Path dest = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(dest);
                    }
                    else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                catch (IOException e) {
                    throw new ServiceException("复制模板文件失败");
                }
            });
        }
    }

    private boolean shouldSkip(Path relative) {
        for (Path part : relative) {
            if (SKIP_DIR_NAMES.contains(part.toString())) {
                return true;
            }
            if (SKIP_FILE_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private void unzip(InputStream in, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(targetDir.resolve(entry.getName()));
                }
                else {
                    Path dest = targetDir.resolve(entry.getName());
                    Files.createDirectories(dest.getParent());
                    try (OutputStream out = Files.newOutputStream(dest)) {
                        zis.transferTo(out);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void deleteQuietly(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            log.warn("清理临时目录失败: {}", root, e);
        }
    }
}
