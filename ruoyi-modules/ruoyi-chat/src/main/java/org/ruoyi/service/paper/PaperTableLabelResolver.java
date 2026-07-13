package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将 SQL 物理表名解析为论文正文使用的中文表名：优先表 COMMENT，其次模块词典，最后英文表名美化。
 */
final class PaperTableLabelResolver {

    private static final Pattern MOSTLY_ASCII = Pattern.compile("^[\\w\\s._-]+$");

    private PaperTableLabelResolver() {
    }

    /** 实体/模块名（不带「表」），如「用户信息」 */
    static String resolveEntityLabel(String tableName, PaperSession.SqlParsed sqlParsed) {
        if (StringUtils.isBlank(tableName)) {
            return "";
        }
        String fromComment = tableComment(tableName, sqlParsed);
        if (StringUtils.isNotBlank(fromComment)) {
            return stripTableSuffix(fromComment.trim());
        }
        String module = PaperModuleDictionary.inferModuleName(tableName);
        if (StringUtils.isNotBlank(module)) {
            return stripTableSuffix(module.replace("管理", "").replace("功能", "").trim());
        }
        String humanized = humanizeTableName(tableName);
        String fromHumanized = PaperModuleDictionary.inferModuleName(humanized.replace(' ', '_'));
        if (StringUtils.isNotBlank(fromHumanized)) {
            return stripTableSuffix(fromHumanized.replace("管理", "").replace("功能", "").trim());
        }
        return humanized;
    }

    /** 是否含汉字（用于判断能否作为论文目录/正文中文名） */
    static boolean isChineseLabel(String label) {
        if (StringUtils.isBlank(label)) {
            return false;
        }
        return label.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
    }

    /** 是否为未翻译的英文表名碎片（如 wallet、home page） */
    static boolean isEnglishTableFragment(String label) {
        if (StringUtils.isBlank(label) || isChineseLabel(label)) {
            return false;
        }
        return MOSTLY_ASCII.matcher(label.trim()).matches();
    }

    /** 论文正文用表名（带「表」），如「用户信息表」 */
    static String resolveTableLabel(String tableName, PaperSession.SqlParsed sqlParsed) {
        if (StringUtils.isBlank(tableName)) {
            return "";
        }
        String label = resolveEntityLabel(tableName, sqlParsed);
        if (StringUtils.isBlank(label)) {
            return tableName;
        }
        if (label.endsWith("表")) {
            return label;
        }
        return label + "表";
    }

    static String joinTableLabels(List<String> tables, PaperSession.SqlParsed sqlParsed) {
        if (tables == null || tables.isEmpty()) {
            return "（未提供功能模块）";
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> labels = new ArrayList<>();
        for (String table : tables) {
            String label = resolveTableLabel(table, sqlParsed);
            if (StringUtils.isBlank(label) || !seen.add(normalizeKey(label))) {
                continue;
            }
            labels.add(label);
        }
        return labels.isEmpty() ? "（未提供功能模块）" : String.join("、", labels);
    }

    static String tableComment(String tableName, PaperSession.SqlParsed sqlParsed) {
        if (sqlParsed == null || sqlParsed.getTableComments() == null || StringUtils.isBlank(tableName)) {
            return null;
        }
        return sqlParsed.getTableComments().get(tableName);
    }

    static String stripTableSuffix(String label) {
        if (StringUtils.isBlank(label)) {
            return label;
        }
        String trimmed = label.trim();
        if (trimmed.endsWith("表") && trimmed.length() > 1) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String humanizeTableName(String tableName) {
        String bare = tableName.replaceAll("(?i)^(sys_|tb_|t_|biz_|b_|base_)", "")
            .replace('_', ' ')
            .trim();
        return bare.length() >= 2 ? bare : tableName;
    }

    private static String normalizeKey(String label) {
        return label.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
