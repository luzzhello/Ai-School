package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.domain.paper.TocNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 导出前模拟排版，估算各章节标题所在页码（用于静态目录页）。
 * <p>
 * 按 A4 + 2.5cm 边距 + 小四 1.5 倍行距估算，与 {@link WordExportService} 写入逻辑对齐。
 */
final class PaperExportLayoutEstimator {

    /** 正文区每页约可容纳行数（小四 + 1.5 倍行距） */
    private static final int LINES_PER_PAGE = 28;
    /** 正文段落每行约汉字数 */
    private static final int CHARS_PER_LINE = 38;

    private static final Pattern FIGURE_CAPTION = Pattern.compile("^图\\s*\\d+[.\\-－]?\\d*.*");
    private static final Pattern TABLE_CAPTION = Pattern.compile("^表\\s*\\d+[.\\-－]?\\d*.*");

    private int currentPage = 1;
    private int linesOnPage = 0;
    private final Map<String, Integer> headingPages = new LinkedHashMap<>();

    Map<String, Integer> estimate(List<TocNode> toc, PaperSession session) {
        headingPages.clear();
        currentPage = 1;
        linesOnPage = 0;

        if (StringUtils.isNotBlank(session.getTitle())) {
            addLines(4);
        }

        boolean tocPageInserted = false;
        boolean needMajorChapterPageBreak = false;
        for (TocNode node : toc) {
            if (isTocPageNode(node)) {
                continue;
            }
            if (!tocPageInserted && !isAbstractNode(node)) {
                insertTocPage(toc);
                tocPageInserted = true;
            }
            if (needMajorChapterPageBreak && !isAbstractNode(node)) {
                addPageBreak();
            }
            simulateNode(node, session);
            if (!tocPageInserted && isAbstractNode(node)) {
                insertTocPage(toc);
                tocPageInserted = true;
            }
            if (!isAbstractNode(node)) {
                needMajorChapterPageBreak = true;
            }
        }
        return headingPages;
    }

    private void simulateNode(TocNode node, PaperSession session) {
        if (isAbstractNode(node) || isReferenceNode(node)) {
            addLines(2);
        } else {
            headingPages.put(node.getId(), currentPage);
            int level = node.getLevel() == null ? 1 : node.getLevel();
            addLines(level <= 1 ? 2 : 1);
        }

        String content = session.getGeneratedContent() == null
            ? null
            : session.getGeneratedContent().get(node.getId());
        if (StringUtils.isBlank(content) && isReferenceNode(node)) {
            simulateReferences(session.getReferences());
        } else if (StringUtils.isNotBlank(content)) {
            addLines(estimateContentLines(content));
        }

        if (node.getChildren() != null) {
            for (TocNode child : node.getChildren()) {
                simulateNode(child, session);
            }
        }
    }

    private void insertTocPage(List<TocNode> toc) {
        addPageBreak();
        addLines(3);
        addLines(countTocEntries(toc));
        // 目录之后正文阿拉伯页码从 1 起，与 Word 分节一致
        currentPage = 1;
        linesOnPage = 0;
    }

    private int countTocEntries(List<TocNode> toc) {
        int count = 0;
        for (TocNode node : toc) {
            count += countTocEntry(node);
        }
        return count;
    }

    private int countTocEntry(TocNode node) {
        if (isExcludedFromTocEntry(node)) {
            int childCount = 0;
            if (node.getChildren() != null) {
                for (TocNode child : node.getChildren()) {
                    childCount += countTocEntry(child);
                }
            }
            return childCount;
        }
        int count = 1;
        if (node.getChildren() != null) {
            for (TocNode child : node.getChildren()) {
                count += countTocEntry(child);
            }
        }
        return count;
    }

    private void simulateReferences(List<Reference> references) {
        if (references == null || references.isEmpty()) {
            return;
        }
        addLines(references.size());
    }

    private int estimateContentLines(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        int total = 0;
        boolean inCode = false;
        int i = 0;
        while (i < lines.length) {
            String trim = lines[i].strip();
            if (trim.startsWith("```")) {
                inCode = !inCode;
                i++;
                continue;
            }
            if (inCode) {
                total++;
                i++;
                continue;
            }
            if (trim.isEmpty()) {
                i++;
                continue;
            }
            // 与 WordExportService 一致：跳过插图编辑元数据
            if (trim.startsWith("[[[PAPER_DRAW:")
                || trim.startsWith("<<<PAPER_DRAW:")
                || (trim.startsWith("<!--") && trim.contains("paper-draw"))
                || "<<>>".equals(trim)) {
                if ((trim.startsWith("[[[PAPER_DRAW:") && !trim.contains("]]]"))
                    || (trim.startsWith("<<<PAPER_DRAW:") && !trim.contains(">>>"))
                    || (trim.startsWith("<!--") && !trim.contains("-->"))) {
                    int j = i + 1;
                    while (j < lines.length && j - i < 30) {
                        String next = lines[j].strip();
                        j++;
                        if (next.contains("]]]") || next.contains(">>>") || next.contains("-->")) {
                            break;
                        }
                    }
                    i = j;
                } else {
                    i++;
                }
                continue;
            }
            if (isTableLine(trim)) {
                int j = i;
                int tableRows = 0;
                while (j < lines.length && isTableLine(lines[j].strip())) {
                    if (!isSeparatorLine(lines[j].strip())) {
                        tableRows++;
                    }
                    j++;
                }
                total += Math.max(tableRows, 1) + 1;
                i = j;
                continue;
            }
            if (FIGURE_CAPTION.matcher(trim).matches() || TABLE_CAPTION.matcher(trim).matches()) {
                total += 1;
                i++;
                continue;
            }
            total += estimateWrappedLines(stripMarkdown(trim));
            i++;
        }
        return total;
    }

    private int estimateWrappedLines(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        return Math.max(1, (text.length() + CHARS_PER_LINE - 1) / CHARS_PER_LINE);
    }

    private void addLines(int lines) {
        if (lines <= 0) {
            return;
        }
        linesOnPage += lines;
        while (linesOnPage > LINES_PER_PAGE) {
            linesOnPage -= LINES_PER_PAGE;
            currentPage++;
        }
    }

    private void addPageBreak() {
        currentPage++;
        linesOnPage = 0;
    }

    private static boolean isTableLine(String line) {
        if (!line.contains("|")) {
            return false;
        }
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '|') {
                count++;
            }
        }
        return count >= 2;
    }

    private static boolean isSeparatorLine(String line) {
        String t = line.replace("|", "").replace(":", "").strip();
        return !t.isEmpty() && t.chars().allMatch(ch -> ch == '-' || ch == ' ');
    }

    private static String stripMarkdown(String text) {
        String t = text;
        t = t.replaceAll("^#{1,6}\\s*", "");
        t = t.replace("**", "").replace("__", "");
        t = t.replaceAll("^[*\\-]\\s+", "");
        return t;
    }

    private static boolean isReferenceNode(TocNode node) {
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        String title = node.getTitle() == null ? "" : node.getTitle();
        return id.contains("reference") || title.contains("参考文献");
    }

    private static boolean isAbstractNode(TocNode node) {
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        String title = node.getTitle() == null ? "" : node.getTitle();
        return "abstract".equals(id) || title.contains("摘要");
    }

    private static boolean isTocPageNode(TocNode node) {
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        String title = node.getTitle() == null ? "" : node.getTitle();
        return id.equals("toc") || id.equals("catalog") || title.equals("目录");
    }

    private static boolean isExcludedFromTocEntry(TocNode node) {
        return isAbstractNode(node) || isTocPageNode(node) || isReferenceNode(node);
    }
}
