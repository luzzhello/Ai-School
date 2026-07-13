package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.dto.request.ErSqlOptimizeRequest;
import org.ruoyi.service.draw.IErDiagramService;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 论文 SQL 在解析 / 绘制 ER 图前，按需 AI 补全外键与中文注释。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperSqlErOptimizer {

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+TABLE",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern FOREIGN_KEY = Pattern.compile(
        "FOREIGN\\s+KEY",
        Pattern.CASE_INSENSITIVE);

    private final IErDiagramService erDiagramService;

    /**
     * 多表且缺少外键声明时，需 AI 优化后才能正确解析实体关联。
     */
    public boolean needsOptimization(String sql) {
        if (StringUtils.isBlank(sql)) {
            return false;
        }
        int tableCount = countMatches(CREATE_TABLE, sql);
        int fkCount = countMatches(FOREIGN_KEY, sql);
        if (tableCount <= 1) {
            return false;
        }
        return fkCount == 0;
    }

    /**
     * 若缺少外键则 AI 优化；失败时回退原始 SQL。
     */
    public OptimizeResult optimizeIfNeeded(String sql) {
        if (!needsOptimization(sql)) {
            return new OptimizeResult(sql, false);
        }
        try {
            ErSqlOptimizeRequest request = new ErSqlOptimizeRequest();
            request.setSql(sql.trim());
            String optimized = erDiagramService.optimizeSql(request).getSql();
            if (StringUtils.isNotBlank(optimized)) {
                log.info("论文 SQL 已 AI 优化（补充外键与注释）");
                return new OptimizeResult(optimized.trim(), true);
            }
        } catch (Exception e) {
            log.warn("论文 SQL AI 优化失败，使用原始 SQL: {}", e.getMessage());
        }
        return new OptimizeResult(sql, false);
    }

    private static int countMatches(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public record OptimizeResult(String sql, boolean optimized) {
    }
}
