package org.ruoyi.service.draw.impl;

import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 CREATE TABLE SQL 解析为陈氏 ER 图实体与关系（不调用外部 AI）
 */
final class SqlChenErParser {

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`'\"]?([\\w$]+)[`'\"]?\\s*\\(",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 支持 COMMENT '...' 与 COMMENT = '...' */
    private static final Pattern TABLE_COMMENT = Pattern.compile(
        "COMMENT\\s*=?\\s*'([^']*)'",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern COLUMN_COMMENT = Pattern.compile(
        "COMMENT\\s*=?\\s*'([^']*)'",
        Pattern.CASE_INSENSITIVE);

    /** 表内 FOREIGN KEY，可选关系 COMMENT（如 COMMENT '选修'） */
    private static final Pattern FK_LINE = Pattern.compile(
        "FOREIGN\\s+KEY\\s*\\([^)]+\\)\\s*REFERENCES\\s+[`'\"]?([\\w$]+)[`'\"]?(?:\\s*\\([^)]*\\))?",
        Pattern.CASE_INSENSITIVE);

    private SqlChenErParser() {
    }

    record ParseResult(List<ChenErLayoutBuilder.EntityDef> entities,
                       List<ChenErLayoutBuilder.RelationshipDef> relationships) {
    }

    record TableDef(String tableName, String entityLabel, List<String> attributes) {
    }

    static ParseResult parse(String sql) {
        if (StringUtils.isBlank(sql)) {
            throw new ServiceException("SQL 语句不能为空");
        }
        String normalized = sql.replace("\r\n", "\n").trim();
        List<TableDef> tables = parseTables(normalized);
        if (tables.isEmpty()) {
            throw new ServiceException("未识别到 CREATE TABLE 语句，请检查 SQL 格式");
        }
        List<ChenErLayoutBuilder.EntityDef> entities = new ArrayList<>();
        Map<String, String> tableToEntity = new LinkedHashMap<>();
        for (TableDef table : tables) {
            tableToEntity.put(table.tableName().toLowerCase(Locale.ROOT), table.entityLabel());
            List<String> attrs = table.attributes().isEmpty()
                ? List.of("编号")
                : table.attributes().size() > 6
                    ? table.attributes().subList(0, 6)
                    : table.attributes();
            entities.add(new ChenErLayoutBuilder.EntityDef(table.entityLabel(), attrs));
        }
        List<ChenErLayoutBuilder.RelationshipDef> relationships = parseForeignKeys(normalized, tableToEntity);
        return new ParseResult(entities, relationships);
    }

    static int countTables(String sql) {
        if (StringUtils.isBlank(sql)) {
            return 0;
        }
        return parseTables(sql.replace("\r\n", "\n").trim()).size();
    }

    private static List<TableDef> parseTables(String sql) {
        List<TableDef> tables = new ArrayList<>();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            int bodyStart = matcher.end();
            int bodyEnd = findMatchingParenEnd(sql, bodyStart - 1);
            if (bodyEnd < 0) {
                continue;
            }
            String body = sql.substring(bodyStart, bodyEnd);
            String tail = sql.substring(bodyEnd, Math.min(sql.length(), bodyEnd + 120));
            String entityLabel = resolveEntityLabel(tableName, body, tail);
            List<String> attributes = parseColumns(body);
            tables.add(new TableDef(tableName, entityLabel, attributes));
        }
        return tables;
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
            }
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String resolveEntityLabel(String tableName, String body, String tailAfterClose) {
        Matcher comment = TABLE_COMMENT.matcher(tailAfterClose);
        if (comment.find()) {
            String label = comment.group(1).trim();
            if (StringUtils.isNotBlank(label)) {
                return stripTableSuffix(label);
            }
        }
        Matcher bodyComment = TABLE_COMMENT.matcher(body);
        String last = null;
        while (bodyComment.find()) {
            last = bodyComment.group(1);
        }
        if (StringUtils.isNotBlank(last) && last.contains("表")) {
            return stripTableSuffix(last.trim());
        }
        return humanizeTableName(tableName);
    }

    private static String stripTableSuffix(String label) {
        String s = label.trim();
        if (s.endsWith("表") && s.length() > 1) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String humanizeTableName(String tableName) {
        String name = tableName.replace('`', ' ').trim();
        if (name.startsWith("t_") || name.startsWith("tb_")) {
            name = name.substring(name.indexOf('_') + 1);
        }
        return name.replace('_', ' ');
    }

    private static List<String> parseColumns(String body) {
        List<String> attributes = new ArrayList<>();
        String[] lines = body.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("PRIMARY KEY")
                || upper.startsWith("UNIQUE KEY")
                || upper.startsWith("KEY ")
                || upper.startsWith("INDEX ")
                || upper.startsWith("CONSTRAINT ")
                || upper.startsWith("FOREIGN KEY")
                || upper.startsWith("CHECK ")
                || upper.equals(")")) {
                continue;
            }
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1).trim();
            }
            String colName = extractColumnName(line);
            if (StringUtils.isBlank(colName)) {
                continue;
            }
            String label = extractColumnComment(line);
            if (StringUtils.isBlank(label)) {
                label = humanizeColumnName(colName);
            }
            if (!attributes.contains(label)) {
                attributes.add(label);
            }
        }
        return attributes;
    }

    private static String extractColumnName(String line) {
        Matcher m = Pattern.compile("^[`'\"]?([\\w$]+)[`'\"]?").matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private static String extractColumnComment(String line) {
        Matcher m = COLUMN_COMMENT.matcher(line);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static String extractFkRelationComment(String fkLine) {
        Matcher m = COLUMN_COMMENT.matcher(fkLine);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        if (StringUtils.isBlank(last)) {
            return "关联";
        }
        return last.trim();
    }

    private static String humanizeColumnName(String colName) {
        if ("id".equalsIgnoreCase(colName)) {
            return "编号";
        }
        return colName.replace('_', ' ');
    }

    private static List<ChenErLayoutBuilder.RelationshipDef> parseForeignKeys(
        String sql,
        Map<String, String> tableToEntity
    ) {
        List<ChenErLayoutBuilder.RelationshipDef> relationships = new ArrayList<>();

        Matcher createMatcher = CREATE_TABLE.matcher(sql);
        while (createMatcher.find()) {
            String fromTable = createMatcher.group(1).toLowerCase(Locale.ROOT);
            int bodyStart = createMatcher.end();
            int bodyEnd = findMatchingParenEnd(sql, bodyStart - 1);
            if (bodyEnd < 0) {
                continue;
            }
            String body = sql.substring(bodyStart, bodyEnd);
            for (String rawLine : body.split("\n")) {
                String line = rawLine.trim();
                if (!line.toUpperCase(Locale.ROOT).startsWith("FOREIGN KEY")) {
                    continue;
                }
                Matcher fkMatcher = FK_LINE.matcher(line);
                if (!fkMatcher.find()) {
                    continue;
                }
                String refTable = fkMatcher.group(1).toLowerCase(Locale.ROOT);
                String relName = extractFkRelationComment(line);
                addRelationship(relationships, tableToEntity, fromTable, refTable, relName);
            }
        }
        return relationships;
    }

    private static void addRelationship(
        List<ChenErLayoutBuilder.RelationshipDef> relationships,
        Map<String, String> tableToEntity,
        String fromTable,
        String refTable,
        String relName
    ) {
        if (StringUtils.isBlank(fromTable) || StringUtils.isBlank(refTable) || fromTable.equals(refTable)) {
            return;
        }
        String entityA = tableToEntity.get(fromTable);
        String entityB = tableToEntity.get(refTable);
        if (StringUtils.isBlank(entityA) || StringUtils.isBlank(entityB)) {
            return;
        }
        for (ChenErLayoutBuilder.RelationshipDef existing : relationships) {
            if (entityA.equals(existing.entityA()) && entityB.equals(existing.entityB())) {
                return;
            }
        }
        String name = StringUtils.isNotBlank(relName) ? relName : "关联";
        relationships.add(new ChenErLayoutBuilder.RelationshipDef(name, entityA, entityB, "n", "1"));
    }
}
