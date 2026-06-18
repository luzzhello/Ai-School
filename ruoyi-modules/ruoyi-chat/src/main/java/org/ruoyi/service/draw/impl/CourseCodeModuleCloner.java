package org.ruoyi.service.draw.impl;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 基于 Courses 模块克隆新的增删改查代码
 */
@Slf4j
@Component
class CourseCodeModuleCloner {

    private static final Set<String> BUILTIN_TABLES = Set.of(
        "students", "courses", "enrollments", "tb_user", "user"
    );

    void generateFromSql(
        Path workDir,
        List<SqlTableDocParser.SqlTableDef> tables,
        String author,
        String today) throws IOException {
        if (tables == null || tables.isEmpty()) {
            return;
        }
        List<String> routeSnippets = new ArrayList<>();
        for (SqlTableDocParser.SqlTableDef table : tables) {
            String tableName = table.tableName().toLowerCase(Locale.ROOT);
            if (BUILTIN_TABLES.contains(tableName)) {
                continue;
            }
            ModuleNames names = resolveNames(table);
            cloneBackendModule(workDir, names, table, author, today);
            cloneFrontendModule(workDir, names, table);
            routeSnippets.add(buildRouteSnippet(names, table));
        }
        if (!routeSnippets.isEmpty()) {
            appendRoutes(workDir, routeSnippets);
        }
    }

    private void cloneBackendModule(
        Path workDir,
        ModuleNames names,
        SqlTableDocParser.SqlTableDef table,
        String author,
        String today) throws IOException {
        copyAndReplace(
            workDir.resolve("template-backend/src/main/java/com/cxk/template/domain/entity/CoursesEntity.java"),
            workDir.resolve("template-backend/src/main/java/com/cxk/template/domain/entity/" + names.entity() + "Entity.java"),
            names,
            table,
            author,
            today
        );
        copyAndReplace(
            workDir.resolve("template-backend/src/main/java/com/cxk/template/domain/vo/CoursesVO.java"),
            workDir.resolve("template-backend/src/main/java/com/cxk/template/domain/vo/" + names.entity() + "VO.java"),
            names,
            table,
            author,
            today
        );
        copyAndReplace(
            workDir.resolve("template-backend/src/main/java/com/cxk/template/mapper/CoursesMapper.java"),
            workDir.resolve("template-backend/src/main/java/com/cxk/template/mapper/" + names.entity() + "Mapper.java"),
            names,
            table,
            author,
            today
        );
        copyAndReplace(
            workDir.resolve("template-backend/src/main/java/com/cxk/template/service/CoursesService.java"),
            workDir.resolve("template-backend/src/main/java/com/cxk/template/service/" + names.entity() + "Service.java"),
            names,
            table,
            author,
            today
        );
        copyAndReplace(
            workDir.resolve("template-backend/src/main/java/com/cxk/template/controller/CoursesController.java"),
            workDir.resolve("template-backend/src/main/java/com/cxk/template/controller/" + names.entity() + "Controller.java"),
            names,
            table,
            author,
            today
        );
        copyAndReplace(
            workDir.resolve("template-backend/src/main/resources/mapper/CoursesMapper.xml"),
            workDir.resolve("template-backend/src/main/resources/mapper/" + names.entity() + "Mapper.xml"),
            names,
            table,
            author,
            today
        );
    }

    private void cloneFrontendModule(Path workDir, ModuleNames names, SqlTableDocParser.SqlTableDef table) throws IOException {
        Path sourcePageDir = workDir.resolve("template-frontend/src/pages/Courses");
        Path targetPageDir = workDir.resolve("template-frontend/src/pages/" + names.entity());
        if (!Files.exists(sourcePageDir)) {
            return;
        }
        copyDirectory(sourcePageDir, targetPageDir, names, table);
    }

    private void copyAndReplace(
        Path source,
        Path target,
        ModuleNames names,
        SqlTableDocParser.SqlTableDef table,
        String author,
        String today) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        String content = Files.readString(source, StandardCharsets.UTF_8);
        content = applyReplacements(content, names, table, author, today);
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private void copyDirectory(
        Path sourceDir,
        Path targetDir,
        ModuleNames names,
        SqlTableDocParser.SqlTableDef table) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            stream.forEach(source -> {
                try {
                    Path relative = sourceDir.relativize(source);
                    Path target = targetDir.resolve(relative);
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                        return;
                    }
                    Files.createDirectories(target.getParent());
                    String content = Files.readString(source, StandardCharsets.UTF_8);
                    content = applyReplacements(content, names, table, "", "");
                    Files.writeString(target, content, StandardCharsets.UTF_8);
                }
                catch (IOException e) {
                    log.warn("克隆前端页面失败: {}", source, e);
                }
            });
        }
    }

    private String applyReplacements(
        String content,
        ModuleNames names,
        SqlTableDocParser.SqlTableDef table,
        String author,
        String today) {
        String comment = StringUtils.defaultIfBlank(table.displayTitle(), names.entity() + "表");
        return content
            .replace("CoursesEntity", names.entity() + "Entity")
            .replace("CoursesVO", names.entity() + "VO")
            .replace("CoursesService", names.entity() + "Service")
            .replace("CoursesMapper", names.entity() + "Mapper")
            .replace("CoursesController", names.entity() + "Controller")
            .replace("coursesController", names.entityLower() + "Controller")
            .replace("removeCoursess", "remove" + names.entity() + "s")
            .replace("saveCourses", "save" + names.entity())
            .replace("updateCourses", "update" + names.entity())
            .replace("getCoursesByPage", "get" + names.entity() + "ByPage")
            .replace("Courses", names.entity())
            .replace("courses", names.entityLower())
            .replace("课程表", comment)
            .replace("@TableName(\"Courses\")", "@TableName(\"" + table.tableName() + "\")")
            .replace("@author 校园小助手", "@author " + StringUtils.defaultIfBlank(author, "校园小助手"))
            .replace("2026/06/03", StringUtils.defaultIfBlank(today, "2026/06/03"));
    }

    private void appendRoutes(Path workDir, List<String> snippets) throws IOException {
        Path routes = workDir.resolve("template-frontend/config/routes.ts");
        if (!Files.exists(routes)) {
            return;
        }
        String content = Files.readString(routes, StandardCharsets.UTF_8);
        int idx = content.lastIndexOf("];");
        if (idx < 0) {
            return;
        }
        StringBuilder sb = new StringBuilder(content.substring(0, idx));
        for (String snippet : snippets) {
            sb.append(",\n  ").append(snippet);
        }
        sb.append("\n];");
        Files.writeString(routes, sb.toString(), StandardCharsets.UTF_8);
    }

    private String buildRouteSnippet(ModuleNames names, SqlTableDocParser.SqlTableDef table) {
        String title = StringUtils.defaultIfBlank(table.displayTitle(), names.entity() + "管理");
        return "{\n    name: \"" + title + "\",\n    icon: 'user',\n    path: '/"
            + names.entityLower() + "',\n    component: './" + names.entity() + "',\n  }";
    }

    private ModuleNames resolveNames(SqlTableDocParser.SqlTableDef table) {
        String raw = table.tableName();
        String pascal = toPascalCase(raw);
        if (!pascal.endsWith("s") && !pascal.endsWith("S")) {
            pascal = pascal + "s";
        }
        String lower = pascal.substring(0, 1).toLowerCase(Locale.ROOT) + pascal.substring(1);
        return new ModuleNames(pascal, lower);
    }

    private String toPascalCase(String name) {
        if (StringUtils.isBlank(name)) {
            return "Main";
        }
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? "Main" : sb.toString();
    }

    private record ModuleNames(String entity, String entityLower) {
    }
}
