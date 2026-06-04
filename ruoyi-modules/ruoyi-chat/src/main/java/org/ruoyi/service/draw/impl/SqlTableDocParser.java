package org.ruoyi.service.draw.impl;

import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 CREATE TABLE SQL 为文档表结构（与前端 parseSql.ts 对齐）
 */
final class SqlTableDocParser {

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`'\"]?([\\w$]+)[`'\"]?\\s*\\(",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern TABLE_COMMENT = Pattern.compile(
        "COMMENT\\s*=?\\s*'([^']*)'",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern COLUMN_COMMENT = Pattern.compile(
        "COMMENT\\s*=?\\s*'([^']*)'",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern PRIMARY_KEY_GROUP = Pattern.compile(
        "PRIMARY\\s+KEY\\s*\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE);

    private static final String[] SKIP_LINE_PREFIXES = {
        "PRIMARY KEY",
        "UNIQUE KEY",
        "KEY ",
        "INDEX ",
        "CONSTRAINT ",
        "FOREIGN KEY",
        "CHECK "
    };

    private SqlTableDocParser() {
    }

    record SqlColumnRow(
        String name,
        String type,
        String length,
        boolean primaryKey,
        String remark,
        String constraint
    ) {
    }

    record SqlTableDef(
        String tableName,
        String displayTitle,
        String captionName,
        List<SqlColumnRow> columns
    ) {
    }

    static List<SqlTableDef> parse(String sql) {
        if (StringUtils.isBlank(sql)) {
            return List.of();
        }
        String normalized = sql.replace("\r\n", "\n").trim();
        List<SqlTableDef> tables = new ArrayList<>();
        Matcher matcher = CREATE_TABLE.matcher(normalized);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            int bodyStart = matcher.end();
            int bodyEnd = findMatchingParenEnd(normalized, bodyStart - 1);
            if (bodyEnd < 0) {
                continue;
            }
            String body = normalized.substring(bodyStart, bodyEnd);
            String tail = normalized.substring(bodyEnd, Math.min(normalized.length(), bodyEnd + 120));
            tables.add(parseTableBody(tableName, body, tail));
        }
        if (tables.isEmpty()) {
            throw new ServiceException("未识别到有效的 CREATE TABLE 语句");
        }
        return tables;
    }

    private static SqlTableDef parseTableBody(String tableName, String body, String tail) {
        TitleInfo title = resolveTableComment(tableName, tail);
        List<SqlColumnRow> columns = new ArrayList<>();
        for (String rawLine : body.split("\n")) {
            SqlColumnRow row = parseColumnLine(rawLine);
            if (row != null) {
                columns.add(row);
            }
        }
        applyCompositePrimaryKeys(body, columns);
        return new SqlTableDef(tableName, title.displayTitle(), title.captionName(), columns);
    }

    private record TitleInfo(String displayTitle, String captionName) {
    }

    private static TitleInfo resolveTableComment(String tableName, String tail) {
        Matcher m = TABLE_COMMENT.matcher(tail);
        if (m.find()) {
            String cn = m.group(1).trim();
            if (StringUtils.isNotBlank(cn)) {
                return new TitleInfo(cn + "(" + tableName + ")", stripTableSuffix(cn));
            }
        }
        return new TitleInfo(tableName, tableName);
    }

    private static String stripTableSuffix(String label) {
        String s = label.trim();
        if (s.endsWith("表") && s.length() > 1) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static SqlColumnRow parseColumnLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("--")) {
            return null;
        }
        if (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        for (String prefix : SKIP_LINE_PREFIXES) {
            if (upper.startsWith(prefix)) {
                return null;
            }
        }
        Matcher nameMatcher = Pattern.compile("^[`'\"]?([\\w$]+)[`'\"]?").matcher(trimmed);
        if (!nameMatcher.find()) {
            return null;
        }
        String name = nameMatcher.group(1);
        String afterName = trimmed.substring(trimmed.indexOf(name) + name.length()).trim();
        Matcher typeMatcher = Pattern.compile("^([A-Za-z]+(?:\\s*\\([^)]*\\))?)").matcher(afterName);
        String typeToken = typeMatcher.find() ? typeMatcher.group(1).replaceAll("\\s+", "") : "VARCHAR";
        TypeInfo typeInfo = parseTypeToken(typeToken);
        return new SqlColumnRow(
            name,
            typeInfo.type(),
            typeInfo.length(),
            Pattern.compile("\\bPRIMARY\\s+KEY\\b", Pattern.CASE_INSENSITIVE).matcher(trimmed).find(),
            extractColumnComment(trimmed),
            extractConstraints(trimmed, upper)
        );
    }

    private record TypeInfo(String type, String length) {
    }

    private static TypeInfo parseTypeToken(String token) {
        Matcher m = Pattern.compile("^([A-Za-z]+)(?:\\((\\d+(?:,\\d+)?)\\))?").matcher(token);
        if (!m.find()) {
            return new TypeInfo(token, "-");
        }
        String base = m.group(1);
        String len = m.group(2);
        return new TypeInfo(base, len != null ? len : "-");
    }

    private static String extractColumnComment(String line) {
        Matcher m = COLUMN_COMMENT.matcher(line);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String extractConstraints(String line, String upper) {
        List<String> parts = new ArrayList<>();
        if (upper.contains("NOT NULL")) {
            parts.add("NOT NULL");
        }
        if (upper.contains("UNIQUE") && !upper.startsWith("UNIQUE KEY")) {
            parts.add("UNIQUE");
        }
        if (upper.contains("AUTO_INCREMENT")) {
            parts.add("AUTO_INCREMENT");
        }
        Matcher dm = Pattern.compile("\\bDEFAULT\\s+([^,\\s]+(?:\\s*\\([^)]*\\))?)", Pattern.CASE_INSENSITIVE).matcher(line);
        if (dm.find()) {
            parts.add("DEFAULT " + dm.group(1));
        }
        return String.join(" ", parts);
    }

    private static void applyCompositePrimaryKeys(String body, List<SqlColumnRow> columns) {
        Matcher m = PRIMARY_KEY_GROUP.matcher(body);
        if (!m.find()) {
            return;
        }
        Set<String> pkNames = new HashSet<>();
        for (String raw : m.group(1).split(",")) {
            pkNames.add(raw.trim().replaceAll("[`'\"]", ""));
        }
        for (int i = 0; i < columns.size(); i++) {
            SqlColumnRow col = columns.get(i);
            if (pkNames.contains(col.name())) {
                columns.set(i, new SqlColumnRow(
                    col.name(), col.type(), col.length(), true, col.remark(), col.constraint()
                ));
            }
        }
    }

    private static int findMatchingParenEnd(String sql, int openIndex) {
        if (openIndex < 0 || openIndex >= sql.length() || sql.charAt(openIndex) != '(') {
            return -1;
        }
        int depth = 0;
        for (int i = openIndex; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
