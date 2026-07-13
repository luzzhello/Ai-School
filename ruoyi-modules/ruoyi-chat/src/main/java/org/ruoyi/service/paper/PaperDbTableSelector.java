package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.Relation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从全部数据表中挑选 5～10 张核心业务表，用于 4.4.2 数据库表设计。
 */
final class PaperDbTableSelector {

    private static final int MIN_TABLES = 5;
    private static final int MAX_TABLES = 10;

    private PaperDbTableSelector() {
    }

    static List<String> selectKeyBusinessTables(PaperSession.SqlParsed sqlParsed) {
        if (sqlParsed == null || sqlParsed.getTables() == null || sqlParsed.getTables().isEmpty()) {
            return List.of();
        }
        Map<String, Integer> relationScore = buildRelationScore(sqlParsed.getRelations());
        List<ScoredTable> scored = new ArrayList<>();
        for (String table : sqlParsed.getTables()) {
            if (isInfrastructureTable(table) || isJunctionTable(table)) {
                continue;
            }
            int score = relationScore.getOrDefault(table.toLowerCase(Locale.ROOT), 0) * 40;
            int colCount = columnCount(sqlParsed, table);
            score += Math.min(colCount, 12);
            if (StringUtils.isNotBlank(PaperModuleDictionary.inferModuleName(table))) {
                score += 25;
            }
            if (isLowValueBindingTable(table)) {
                score -= 80;
            }
            scored.add(new ScoredTable(table, score));
        }
        scored.sort(Comparator.comparingInt(ScoredTable::score).reversed()
            .thenComparing(ScoredTable::table));

        int take = scored.size() <= MIN_TABLES
            ? scored.size()
            : Math.min(MAX_TABLES, Math.max(MIN_TABLES, scored.size()));
        return scored.stream().limit(take).map(ScoredTable::table).toList();
    }

    private record ScoredTable(String table, int score) {
    }

    private static Map<String, Integer> buildRelationScore(List<Relation> relations) {
        Map<String, Integer> score = new HashMap<>();
        if (relations == null) {
            return score;
        }
        for (Relation relation : relations) {
            bumpRelationScore(score, relation.getTable1());
            bumpRelationScore(score, relation.getTable2());
        }
        return score;
    }

    private static void bumpRelationScore(Map<String, Integer> score, String table) {
        if (StringUtils.isBlank(table) || isInfrastructureTable(table)) {
            return;
        }
        String key = table.toLowerCase(Locale.ROOT);
        score.put(key, score.getOrDefault(key, 0) + 1);
    }

    private static int columnCount(PaperSession.SqlParsed sqlParsed, String table) {
        if (sqlParsed.getColumns() == null || sqlParsed.getColumns().get(table) == null) {
            return 0;
        }
        return sqlParsed.getColumns().get(table).size();
    }

    private static boolean isInfrastructureTable(String table) {
        if (StringUtils.isBlank(table)) {
            return true;
        }
        String lower = table.toLowerCase(Locale.ROOT);
        return lower.startsWith("sys_") || lower.startsWith("qrtz_") || lower.startsWith("act_")
            || lower.startsWith("gen_") || lower.contains("dict") || lower.contains("config")
            || lower.contains("oper_log") || lower.contains("logininfor") || lower.contains("job_log")
            || lower.contains("flyway") || lower.endsWith("_log");
    }

    /** 多对多/关联表，不作为三线表展示 */
    private static boolean isJunctionTable(String table) {
        if (StringUtils.isBlank(table)) {
            return false;
        }
        String lower = table.toLowerCase(Locale.ROOT);
        return lower.contains("user_role") || lower.contains("role_menu") || lower.contains("role_perm")
            || lower.contains("_bind") || lower.contains("_binding") || lower.contains("_rel")
            || lower.contains("_link") || lower.contains("_map") || lower.contains("_ref")
            || (lower.contains("category") && lower.contains("module"));
    }

    private static boolean isLowValueBindingTable(String table) {
        String lower = table.toLowerCase(Locale.ROOT);
        return lower.contains("menu") || lower.contains("permission") || lower.contains("dict")
            || lower.contains("banner") || lower.contains("carousel");
    }
}
