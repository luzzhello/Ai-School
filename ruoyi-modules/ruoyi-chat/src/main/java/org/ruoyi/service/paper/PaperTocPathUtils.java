package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.TocNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从目录树标题/路径解析章节编号，不依赖固定 chapterId。
 */
final class PaperTocPathUtils {

    private static final Pattern TITLE_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s+(.+)$");

    private PaperTocPathUtils() {
    }

    record ParsedTitle(int[] numbers, String path, String bareTitle) {
    }

    static ParsedTitle parseTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return null;
        }
        Matcher matcher = TITLE_PREFIX.matcher(title.trim());
        if (!matcher.matches()) {
            return null;
        }
        String[] parts = matcher.group(1).split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return new ParsedTitle(numbers, matcher.group(1), matcher.group(2).trim());
    }

    static TocNode findNode(List<TocNode> nodes, String chapterId) {
        if (nodes == null || StringUtils.isBlank(chapterId)) {
            return null;
        }
        for (TocNode node : nodes) {
            if (chapterId.equals(node.getId())) {
                return node;
            }
            TocNode found = findNode(node.getChildren(), chapterId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static List<TocNode> findPathToNode(List<TocNode> nodes, String chapterId) {
        List<TocNode> path = new ArrayList<>();
        if (findPathRecursive(nodes, chapterId, path)) {
            return path;
        }
        return List.of();
    }

    private static boolean findPathRecursive(List<TocNode> nodes, String chapterId, List<TocNode> path) {
        if (nodes == null) {
            return false;
        }
        for (TocNode node : nodes) {
            path.add(node);
            if (chapterId.equals(node.getId())) {
                return true;
            }
            if (findPathRecursive(node.getChildren(), chapterId, path)) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }

    /**
     * 解析一级章号（1=绪论, 2=关键技术, 5=实现…）。优先当前标题，其次祖先节点标题。
     */
    static int resolveChapterMajor(TocNode node, List<TocNode> toc) {
        if (node == null) {
            return -1;
        }
        ParsedTitle self = parseTitle(node.getTitle());
        if (self != null && self.numbers.length > 0) {
            return self.numbers[0];
        }
        List<TocNode> path = findPathToNode(toc, node.getId());
        for (int i = path.size() - 1; i >= 0; i--) {
            ParsedTitle ancestor = parseTitle(path.get(i).getTitle());
            if (ancestor != null && ancestor.numbers.length > 0) {
                return ancestor.numbers[0];
            }
        }
        return -1;
    }

    static String sectionPath(TocNode node) {
        ParsedTitle parsed = parseTitle(node == null ? null : node.getTitle());
        return parsed == null ? "" : parsed.path;
    }
}
