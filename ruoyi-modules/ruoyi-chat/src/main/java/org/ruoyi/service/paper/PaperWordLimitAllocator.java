package org.ruoyi.service.paper;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.TocNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 按全文目标字数为各叶子章节分配 {@link TocNode#getWordLimit()}。
 * <p>基准权重按 1.5 万字论文标定，生成大纲或修改字数要求后按比例缩放。
 */
@Slf4j
public final class PaperWordLimitAllocator {

    /** 权重标定基准总字数 */
    static final int BASELINE_TOTAL = 15000;
    static final int DEFAULT_TARGET = 15000;
    static final int MIN_TARGET = 6000;
    static final int MAX_TARGET = 30000;

    private PaperWordLimitAllocator() {
    }

    public static void apply(List<TocNode> toc, Integer targetWordCount) {
        if (toc == null || toc.isEmpty()) {
            return;
        }
        int target = normalizeTarget(targetWordCount);
        List<TocNode> leaves = collectLeaves(toc);
        if (leaves.isEmpty()) {
            return;
        }

        double sum = 0;
        double[] weights = new double[leaves.size()];
        for (int i = 0; i < leaves.size(); i++) {
            TocNode node = leaves.get(i);
            if (shouldSkipLimit(node)) {
                weights[i] = 0;
                node.setWordLimit(null);
                continue;
            }
            weights[i] = baselineWeight(node, toc);
            sum += weights[i];
        }
        if (sum <= 0) {
            return;
        }

        double scale = target / sum;
        int assigned = 0;
        for (int i = 0; i < leaves.size(); i++) {
            if (weights[i] <= 0) {
                continue;
            }
            TocNode node = leaves.get(i);
            int min = minLimit(node);
            int max = maxLimit(node, target);
            int limit = (int) Math.round(weights[i] * scale);
            limit = Math.max(min, Math.min(max, limit));
            node.setWordLimit(limit);
            assigned += limit;
        }
        log.info("论文字数分配完成, target={}, leaves={}, assigned≈{}", target, leaves.size(), assigned);
    }

    private static int normalizeTarget(Integer wordCount) {
        if (wordCount == null || wordCount < MIN_TARGET) {
            return DEFAULT_TARGET;
        }
        return Math.min(wordCount, MAX_TARGET);
    }

    private static List<TocNode> collectLeaves(List<TocNode> nodes) {
        List<TocNode> leaves = new ArrayList<>();
        collectLeavesRecursive(nodes, leaves);
        return leaves;
    }

    private static void collectLeavesRecursive(List<TocNode> nodes, List<TocNode> leaves) {
        if (nodes == null) {
            return;
        }
        for (TocNode node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                collectLeavesRecursive(node.getChildren(), leaves);
            }
            else {
                leaves.add(node);
            }
        }
    }

    private static boolean shouldSkipLimit(TocNode node) {
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        String title = node.getTitle() == null ? "" : node.getTitle();
        return id.contains("reference") || title.contains("参考文献");
    }

    /** 1.5 万字标定下的章节基准权重 */
    private static double baselineWeight(TocNode node, List<TocNode> toc) {
        String title = node.getTitle() == null ? "" : node.getTitle();
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        PaperTocPathUtils.ParsedTitle parsed = PaperTocPathUtils.parseTitle(title);
        String path = parsed == null ? PaperTocPathUtils.sectionPath(node) : parsed.path();
        String bare = parsed == null ? title : parsed.bareTitle();
        int major = PaperTocPathUtils.resolveChapterMajor(node, toc);

        if (PaperChapterPrompts.isAbstractChapter(id, node)) {
            return 900;
        }
        if (title.contains("致谢") || id.contains("acknowledgement") || id.contains("thanks")) {
            return 450;
        }

        if (matchesSection(path, "1.1") || containsAny(bare, title, "研究背景", "背景与意义")) {
            return 500;
        }
        if (matchesSection(path, "1.2.1") || containsAny(bare, title, "国内研究", "国内现状")) {
            return 525;
        }
        if (matchesSection(path, "1.2.2") || containsAny(bare, title, "国外研究", "国外现状")) {
            return 525;
        }
        if (matchesSection(path, "1.2.3") || containsAny(bare, title, "研究现状小结", "研究结论")) {
            return 375;
        }
        if (matchesSection(path, "1.3") || containsAny(bare, title, "研究内容", "开发环境")) {
            return 350;
        }
        if (matchesSection(path, "1.4") || containsAny(bare, title, "结构安排", "论文结构")) {
            return 700;
        }

        if (major == 2 || title.matches("(?i).*2\\.\\d+.*")) {
            return 250;
        }

        if (major == 3 || path.startsWith("3")) {
            if (containsAny(bare, title, "功能需求", "功能模块", "功能分析")) {
                return 680;
            }
            if (containsAny(bare, title, "用例")) {
                return 520;
            }
            if (containsAny(bare, title, "调研")) {
                return 450;
            }
            if (containsAny(bare, title, "非功能")) {
                return 300;
            }
            if (containsAny(bare, title, "可行")) {
                return 200;
            }
            if (containsAny(bare, title, "性能")) {
                return 250;
            }
            if (containsAny(bare, title, "流程", "操作流")) {
                return 350;
            }
            return 380;
        }

        if (major == 4 || path.startsWith("4")) {
            if (containsAny(bare, title, "架构设计", "系统架构") && !containsAny(bare, title, "结构")) {
                return 380;
            }
            if (containsAny(bare, title, "功能结构", "模块设计")) {
                return 360;
            }
            if (containsAny(bare, title, "E-R", "ER图", "实体")) {
                return 420;
            }
            if (containsAny(bare, title, "数据库", "表设计", "表结构")) {
                return 520;
            }
            if (path.matches("4\\.\\d+\\.\\d+") || containsAny(bare, title, "流程")) {
                return 360;
            }
            return 380;
        }

        if (major == 5 || path.startsWith("5")) {
            if (containsAny(bare, title, "本章小结", "小结")) {
                return 200;
            }
            return 300;
        }

        if (major == 6 || path.startsWith("6")) {
            if (matchesSection(path, "6.1") || containsAny(bare, title, "目的")) {
                return 400;
            }
            if (matchesSection(path, "6.2") || containsAny(bare, title, "环境", "工具")) {
                return 320;
            }
            if (matchesSection(path, "6.3") || containsAny(bare, title, "过程", "用例")) {
                return 1300;
            }
            if (matchesSection(path, "6.4") || containsAny(bare, title, "结论", "结果")) {
                return 400;
            }
            if (containsAny(bare, title, "小结")) {
                return 200;
            }
            return 400;
        }

        if (major == 7 || containsAny(bare, title, "总结", "展望")) {
            return 1000;
        }

        return 350;
    }

    private static int minLimit(TocNode node) {
        String title = node.getTitle() == null ? "" : node.getTitle();
        if (PaperChapterPrompts.isAbstractChapter(node.getId(), node)) {
            return 550;
        }
        if (title.contains("致谢")) {
            return 280;
        }
        if (title.contains("6.3") || title.contains("测试过程")) {
            return 600;
        }
        return 120;
    }

    private static int maxLimit(TocNode node, int target) {
        String title = node.getTitle() == null ? "" : node.getTitle();
        if (PaperChapterPrompts.isAbstractChapter(node.getId(), node)) {
            return Math.max(1200, target / 8);
        }
        if (title.contains("6.3") || title.contains("测试过程")) {
            return Math.max(2500, target / 3);
        }
        if (title.contains("功能需求")) {
            return Math.max(1200, target / 6);
        }
        return Math.max(900, target / 4);
    }

    private static boolean matchesSection(String path, String expected) {
        return StringUtils.isNotBlank(path) && (path.equals(expected) || path.startsWith(expected + "."));
    }

    private static boolean containsAny(String bare, String full, String... keywords) {
        for (String kw : keywords) {
            if (full.contains(kw) || (StringUtils.isNotBlank(bare) && bare.contains(kw))) {
                return true;
            }
        }
        return false;
    }
}
