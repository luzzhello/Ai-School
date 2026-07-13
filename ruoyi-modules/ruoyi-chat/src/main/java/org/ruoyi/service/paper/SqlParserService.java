package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.SqlColumnInfo;
import org.ruoyi.domain.paper.PaperSession.SqlParsed;
import org.ruoyi.domain.paper.Relation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 论文生成智能体——SQL 文件解析服务。
 * <p>
 * 不依赖任何数据库驱动，纯字符串/正则解析 .sql 内容，提取表名、字段、外键关系，
 * 并基于表名/字段推断系统功能中文描述。对应 PRD「3.5 / 6.4」。
 */
@Service
public class SqlParserService {

    /** 匹配 CREATE TABLE 表名，末尾包含左括号（group 末位即表体起点） */
    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"\\[]?([A-Za-z0-9_]+)[`\"\\]]?\\s*\\(",
        Pattern.CASE_INSENSITIVE);

    /** 列定义起始的字段名 */
    private static final Pattern COLUMN_NAME = Pattern.compile(
        "^[`\"\\[]?([A-Za-z_][A-Za-z0-9_]*)[`\"\\]]?\\s+(.*)$", Pattern.DOTALL);

    /** 字段类型（含可选长度，如 varchar(255)、decimal(10,2)） */
    private static final Pattern COLUMN_TYPE = Pattern.compile(
        "^([A-Za-z][A-Za-z0-9]*)\\s*(\\([^)]*\\))?", Pattern.CASE_INSENSITIVE);

    /** 列/表注释 COMMENT '...' 或 COMMENT "..." */
    private static final Pattern COMMENT = Pattern.compile(
        "COMMENT\\s+(?:'((?:[^']|'')*)'|\"((?:[^\"]|\"\")*)\")", Pattern.CASE_INSENSITIVE);

    /** 表级主键约束 PRIMARY KEY (`a`,`b`) */
    private static final Pattern PRIMARY_KEY = Pattern.compile(
        "PRIMARY\\s+KEY\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    /** 外键约束 FOREIGN KEY (`col`) REFERENCES `tbl` (`rcol`) */
    private static final Pattern FOREIGN_KEY = Pattern.compile(
        "FOREIGN\\s+KEY\\s*\\(([^)]*)\\)\\s*REFERENCES\\s+[`\"\\[]?([A-Za-z0-9_]+)[`\"\\]]?",
        Pattern.CASE_INSENSITIVE);

    /** 行内 REFERENCES */
    private static final Pattern INLINE_REFERENCES = Pattern.compile(
        "REFERENCES\\s+[`\"\\[]?([A-Za-z0-9_]+)[`\"\\]]?", Pattern.CASE_INSENSITIVE);

    /** 表名常见前缀，推断模块时剥离 */
    private static final String[] TABLE_PREFIXES = {"sys_", "tb_", "t_", "biz_", "b_", "base_", "ums_", "pms_", "oms_", "wms_"};

    /** 关键词 -> 中文模块名，用于推断系统功能描述 */
    private static final Map<String, String> MODULE_DICT = buildModuleDict();

    /**
     * 解析 SQL 文本，返回结构化结果。
     *
     * @param sqlContent .sql 文件字符串内容
     * @return 解析结果（永不为 null）
     */
    public SqlParsed parse(String sqlContent) {
        SqlParsed parsed = new SqlParsed();
        if (StringUtils.isBlank(sqlContent)) {
            return parsed;
        }
        String sql = stripComments(sqlContent);

        List<String> tables = new ArrayList<>();
        Map<String, List<SqlColumnInfo>> columnMap = new LinkedHashMap<>();
        Map<String, String> tableComments = new LinkedHashMap<>();
        List<Relation> relations = new ArrayList<>();

        Matcher m = CREATE_TABLE.matcher(sql);
        while (m.find()) {
            String tableName = m.group(1);
            int bodyStart = m.end();
            int bodyEnd = matchClosingParen(sql, bodyStart);
            if (bodyEnd < 0) {
                continue;
            }
            String body = sql.substring(bodyStart, bodyEnd);

            List<SqlColumnInfo> columns = new ArrayList<>();
            Set<String> pkCols = new LinkedHashSet<>();
            parseTableBody(tableName, body, columns, pkCols, relations);

            // 应用表级主键标记
            for (SqlColumnInfo col : columns) {
                if (pkCols.contains(col.getName().toLowerCase())) {
                    col.setPk(true);
                }
            }

            String tableComment = parseTableComment(sql, bodyEnd + 1);
            if (StringUtils.isNotBlank(tableComment)) {
                tableComments.put(tableName, tableComment);
            }

            tables.add(tableName);
            columnMap.put(tableName, columns);
        }

        // 命名约定推断外键（xxx_id -> xxx 表），补充未显式声明的外键
        inferForeignKeysByNaming(tables, columnMap, relations);

        parsed.setTables(tables);
        parsed.setColumns(columnMap);
        parsed.setTableComments(tableComments);
        parsed.setRelations(relations);
        parsed.setSummary(buildSummary(tables, tableComments));
        return parsed;
    }

    /** 解析 CREATE TABLE 语句尾部 COMMENT='表注释' */
    private String parseTableComment(String sql, int searchFrom) {
        int semi = sql.indexOf(';', searchFrom);
        if (semi < 0) {
            semi = sql.length();
        }
        String trailer = sql.substring(searchFrom, semi);
        Matcher commentMatcher = COMMENT.matcher(trailer);
        if (!commentMatcher.find()) {
            return null;
        }
        String comment = commentMatcher.group(1) != null ? commentMatcher.group(1) : commentMatcher.group(2);
        if (comment == null) {
            return null;
        }
        return comment.replace("''", "'").replace("\"\"", "\"").trim();
    }

    /**
     * 解析单张表体（去除外层括号后的内容），填充字段、主键集合、关系。
     */
    private void parseTableBody(String tableName, String body, List<SqlColumnInfo> columns,
                                Set<String> pkCols, List<Relation> relations) {
        for (String rawSeg : splitTopLevel(body)) {
            String seg = rawSeg.trim();
            if (seg.isEmpty()) {
                continue;
            }
            String upper = seg.toUpperCase();

            // 表级约束
            if (upper.startsWith("PRIMARY KEY")) {
                Matcher pk = PRIMARY_KEY.matcher(seg);
                if (pk.find()) {
                    for (String c : splitCols(pk.group(1))) {
                        pkCols.add(c.toLowerCase());
                    }
                }
                continue;
            }
            if (upper.startsWith("FOREIGN KEY") || upper.startsWith("CONSTRAINT")) {
                Matcher fk = FOREIGN_KEY.matcher(seg);
                if (fk.find()) {
                    String refTable = fk.group(2);
                    for (String c : splitCols(fk.group(1))) {
                        markFk(columns, c);
                        addRelation(relations, refTable, tableName, c, "1:N");
                    }
                }
                continue;
            }
            if (upper.startsWith("UNIQUE") || upper.startsWith("KEY ") || upper.startsWith("KEY(")
                || upper.startsWith("INDEX") || upper.startsWith("FULLTEXT") || upper.startsWith("CHECK")
                || upper.startsWith("SPATIAL")) {
                continue;
            }

            // 普通列
            SqlColumnInfo col = parseColumn(seg);
            if (col == null) {
                continue;
            }
            if (upper.contains("PRIMARY KEY")) {
                col.setPk(true);
            }
            Matcher ref = INLINE_REFERENCES.matcher(seg);
            if (ref.find()) {
                col.setFk(true);
                addRelation(relations, ref.group(1), tableName, col.getName(), "1:N");
            }
            columns.add(col);
        }
    }

    /**
     * 解析单条列定义。
     */
    private SqlColumnInfo parseColumn(String seg) {
        Matcher nameMatcher = COLUMN_NAME.matcher(seg);
        if (!nameMatcher.find()) {
            return null;
        }
        String name = nameMatcher.group(1);
        String rest = nameMatcher.group(2);

        SqlColumnInfo col = new SqlColumnInfo();
        col.setName(name);

        Matcher typeMatcher = COLUMN_TYPE.matcher(rest.trim());
        if (typeMatcher.find()) {
            String type = typeMatcher.group(1);
            if (typeMatcher.group(2) != null) {
                type += typeMatcher.group(2);
            }
            col.setType(type);
        }

        Matcher commentMatcher = COMMENT.matcher(seg);
        if (commentMatcher.find()) {
            String comment = commentMatcher.group(1) != null ? commentMatcher.group(1) : commentMatcher.group(2);
            if (comment != null) {
                col.setComment(comment.replace("''", "'").replace("\"\"", "\""));
            }
        }
        return col;
    }

    /**
     * 通过 xxx_id 命名约定推断外键并补充关系（已有显式外键的列跳过）。
     */
    private void inferForeignKeysByNaming(List<String> tables, Map<String, List<SqlColumnInfo>> columnMap,
                                          List<Relation> relations) {
        Set<String> tableLower = new LinkedHashSet<>();
        for (String t : tables) {
            tableLower.add(t.toLowerCase());
        }
        for (Map.Entry<String, List<SqlColumnInfo>> entry : columnMap.entrySet()) {
            String table = entry.getKey();
            for (SqlColumnInfo col : entry.getValue()) {
                String lower = col.getName().toLowerCase();
                if (col.isPk() || col.isFk() || !lower.endsWith("_id")) {
                    continue;
                }
                String base = lower.substring(0, lower.length() - 3);
                if (base.isEmpty()) {
                    continue;
                }
                String refTable = resolveReferencedTable(base, table, tableLower);
                if (refTable != null) {
                    col.setFk(true);
                    addRelation(relations, refTable, table, col.getName(), "1:N");
                }
            }
        }
    }

    /**
     * 根据外键基名匹配真实表名（尝试原名、复数、带前缀等形式）。
     */
    private String resolveReferencedTable(String base, String currentTable, Set<String> tableLower) {
        List<String> candidates = new ArrayList<>();
        candidates.add(base);
        candidates.add(base + "s");
        candidates.add(base + "es");
        if (base.endsWith("y")) {
            candidates.add(base.substring(0, base.length() - 1) + "ies");
        }
        for (String prefix : TABLE_PREFIXES) {
            candidates.add(prefix + base);
            candidates.add(prefix + base + "s");
        }
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(currentTable)) {
                continue;
            }
            if (tableLower.contains(candidate)) {
                // 返回原始大小写的表名
                for (String t : tableLower) {
                    if (t.equals(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 基于表名推断系统功能中文描述。
     */
    private String buildSummary(List<String> tables, Map<String, String> tableComments) {
        if (tables.isEmpty()) {
            return "未从 SQL 中解析到数据表。";
        }
        Set<String> modules = new LinkedHashSet<>();
        for (String table : tables) {
            String comment = tableComments == null ? null : tableComments.get(table);
            if (StringUtils.isNotBlank(comment)) {
                modules.add(PaperTableLabelResolver.stripTableSuffix(comment.trim()));
                continue;
            }
            String core = stripPrefix(table.toLowerCase());
            String module = matchModule(core);
            if (module != null) {
                modules.add(PaperTableLabelResolver.stripTableSuffix(module.replace("管理", "")));
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!modules.isEmpty()) {
            List<String> list = new ArrayList<>(modules);
            int show = Math.min(list.size(), 8);
            sb.append("本系统包含");
            for (int i = 0; i < show; i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append(list.get(i));
            }
            sb.append("等模块");
            sb.append("，共包含 ").append(tables.size()).append(" 张数据表。");
        } else {
            sb.append("本系统共包含 ").append(tables.size()).append(" 张数据表，涵盖 ");
            int show = Math.min(tables.size(), 6);
            PaperSession.SqlParsed parsed = new PaperSession.SqlParsed();
            parsed.setTableComments(tableComments == null ? new LinkedHashMap<>() : tableComments);
            for (int i = 0; i < show; i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append(PaperTableLabelResolver.resolveTableLabel(tables.get(i), parsed));
            }
            sb.append(" 等业务实体。");
        }
        return sb.toString();
    }

    private String matchModule(String core) {
        if (MODULE_DICT.containsKey(core)) {
            return MODULE_DICT.get(core);
        }
        // 包含匹配（如 order_item 命中 order）
        for (Map.Entry<String, String> e : MODULE_DICT.entrySet()) {
            if (core.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private String stripPrefix(String table) {
        for (String prefix : TABLE_PREFIXES) {
            if (table.startsWith(prefix)) {
                return table.substring(prefix.length());
            }
        }
        return table;
    }

    // ---------------- 工具方法 ----------------

    private void markFk(List<SqlColumnInfo> columns, String colName) {
        for (SqlColumnInfo col : columns) {
            if (col.getName().equalsIgnoreCase(colName)) {
                col.setFk(true);
                return;
            }
        }
    }


    private void addRelation(List<Relation> relations, String table1, String table2, String viaColumn, String type) {
        for (Relation r : relations) {
            if (r.getTable2().equalsIgnoreCase(table2) && viaColumn.equalsIgnoreCase(r.getViaColumn())) {
                return;
            }
        }
        relations.add(new Relation(table1, table2, viaColumn, type));
    }

    /**
     * 从 openIndex 起（已在括号内），返回与之配对的右括号位置（不含），考虑字符串字面量与嵌套括号。
     */
    private int matchClosingParen(String sql, int openIndex) {
        int depth = 1;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        for (int i = openIndex; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                }
            } else if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                }
            } else if (inBacktick) {
                if (c == '`') {
                    inBacktick = false;
                }
            } else {
                switch (c) {
                    case '\'' -> inSingle = true;
                    case '"' -> inDouble = true;
                    case '`' -> inBacktick = true;
                    case '(' -> depth++;
                    case ')' -> {
                        depth--;
                        if (depth == 0) {
                            return i;
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 按顶层逗号切分表体（忽略括号内与字符串内的逗号）。
     */
    private List<String> splitTopLevel(String body) {
        List<String> segs = new ArrayList<>();
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inSingle) {
                cur.append(c);
                if (c == '\'') {
                    inSingle = false;
                }
                continue;
            }
            if (inDouble) {
                cur.append(c);
                if (c == '"') {
                    inDouble = false;
                }
                continue;
            }
            if (inBacktick) {
                cur.append(c);
                if (c == '`') {
                    inBacktick = false;
                }
                continue;
            }
            switch (c) {
                case '\'' -> {
                    inSingle = true;
                    cur.append(c);
                }
                case '"' -> {
                    inDouble = true;
                    cur.append(c);
                }
                case '`' -> {
                    inBacktick = true;
                    cur.append(c);
                }
                case '(' -> {
                    depth++;
                    cur.append(c);
                }
                case ')' -> {
                    depth--;
                    cur.append(c);
                }
                case ',' -> {
                    if (depth == 0) {
                        segs.add(cur.toString());
                        cur.setLength(0);
                    } else {
                        cur.append(c);
                    }
                }
                default -> cur.append(c);
            }
        }
        if (cur.length() > 0) {
            segs.add(cur.toString());
        }
        return segs;
    }

    /**
     * 切分括号内的列名列表（如 `a`,`b`），并去除反引号/引号。
     */
    private List<String> splitCols(String raw) {
        List<String> cols = new ArrayList<>();
        for (String part : raw.split(",")) {
            String c = part.trim().replaceAll("[`\"\\[\\]()]", "");
            // 去掉排序方向等修饰（如 `id` ASC）
            int sp = c.indexOf(' ');
            if (sp > 0) {
                c = c.substring(0, sp);
            }
            if (!c.isEmpty()) {
                cols.add(c);
            }
        }
        return cols;
    }

    /**
     * 去除 SQL 注释：块注释、-- 行注释。
     */
    private String stripComments(String sql) {
        String noBlock = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder sb = new StringBuilder();
        for (String line : noBlock.split("\n", -1)) {
            int idx = line.indexOf("--");
            if (idx >= 0) {
                // 仅当 -- 后跟空白或行尾时视为注释，避免误伤运算
                if (idx + 2 >= line.length() || Character.isWhitespace(line.charAt(idx + 2))) {
                    line = line.substring(0, idx);
                }
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static Map<String, String> buildModuleDict() {
        Map<String, String> dict = new LinkedHashMap<>();
        put(dict, "用户管理", "user", "users", "member", "members", "account", "accounts", "customer", "customers");
        put(dict, "管理员管理", "admin", "admins", "manager");
        put(dict, "角色管理", "role", "roles");
        put(dict, "权限管理", "permission", "permissions", "perm", "perms", "auth");
        put(dict, "菜单管理", "menu", "menus");
        put(dict, "部门管理", "dept", "department", "departments");
        put(dict, "员工管理", "employee", "employees", "staff", "worker");
        put(dict, "订单管理", "order", "orders");
        put(dict, "商品管理", "goods", "product", "products", "item", "items", "commodity", "spu", "sku");
        put(dict, "分类管理", "category", "categories", "cate", "classify", "type", "types");
        put(dict, "购物车管理", "cart", "carts", "shopcart");
        put(dict, "支付管理", "pay", "payment", "payments", "payorder", "bill", "bills");
        put(dict, "库存管理", "stock", "inventory", "warehouse", "repository");
        put(dict, "图书管理", "book", "books");
        put(dict, "借阅管理", "borrow", "borrows", "lend", "loan");
        put(dict, "学生管理", "student", "students");
        put(dict, "教师管理", "teacher", "teachers");
        put(dict, "课程管理", "course", "courses");
        put(dict, "班级管理", "clazz", "classes", "grade");
        put(dict, "成绩管理", "score", "scores", "result", "achievement");
        put(dict, "选课管理", "selection", "elective", "choose");
        put(dict, "文章管理", "article", "articles", "news", "post", "posts", "blog", "blogs");
        put(dict, "评论管理", "comment", "comments", "reply", "replies");
        put(dict, "收藏管理", "favorite", "favorites", "collection", "collect");
        put(dict, "消息管理", "message", "messages", "msg");
        put(dict, "通知管理", "notice", "notices", "notification", "notify");
        put(dict, "日志管理", "log", "logs");
        put(dict, "文件管理", "file", "files", "attachment", "upload");
        put(dict, "地址管理", "address", "addresses");
        put(dict, "供应商管理", "supplier", "suppliers", "vendor");
        put(dict, "客户管理", "client", "clients");
        put(dict, "考勤管理", "attendance", "checkin", "clock");
        put(dict, "薪资管理", "salary", "salaries", "wage");
        put(dict, "房间管理", "room", "rooms", "hotel");
        put(dict, "车辆管理", "car", "cars", "vehicle", "vehicles");
        put(dict, "票务管理", "ticket", "tickets");
        put(dict, "预约管理", "appointment", "reservation", "booking");
        put(dict, "医生管理", "doctor", "doctors");
        put(dict, "患者管理", "patient", "patients");
        put(dict, "活动管理", "activity", "activities", "event", "events");
        put(dict, "轮播图管理", "banner", "banners", "carousel", "slide");
        return dict;
    }

    private static void put(Map<String, String> dict, String module, String... keys) {
        for (String key : keys) {
            dict.put(key, module);
        }
    }
}
