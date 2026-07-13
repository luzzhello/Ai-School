package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 清理 AI 生成正文中重复的大纲/章节标题行（如 {@code ## 2.1 Java简介}）。
 * <p>Word 导出与页面标题栏已展示节标题，正文不应再重复目录编号。
 */
final class PaperChapterContentSanitizer {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s*");
    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s*");

    private PaperChapterContentSanitizer() {
    }

    static String stripDuplicateSectionHeading(String content, String chapterTitle) {
        if (StringUtils.isBlank(content) || StringUtils.isBlank(chapterTitle)) {
            return content == null ? "" : content;
        }
        String result = content.replace("\r\n", "\n");
        String fullTitle = chapterTitle.trim();
        String bareTitle = stripNumericPrefix(fullTitle);

        boolean changed = true;
        while (changed) {
            changed = false;
            result = result.replaceFirst("(?m)^\\s+", "");
            if (result.startsWith("\uFEFF")) {
                result = result.substring(1);
                changed = true;
                continue;
            }
            int lineEnd = result.indexOf('\n');
            String firstLine = lineEnd >= 0 ? result.substring(0, lineEnd) : result;
            if (StringUtils.isBlank(firstLine)) {
                result = lineEnd >= 0 ? result.substring(lineEnd + 1) : "";
                changed = true;
                continue;
            }
            if (isDuplicateHeadingLine(firstLine, fullTitle, bareTitle)) {
                result = lineEnd >= 0 ? result.substring(lineEnd + 1) : "";
                changed = true;
            }
        }
        return result.trim();
    }

    private static boolean isDuplicateHeadingLine(String line, String fullTitle, String bareTitle) {
        String normalizedLine = normalizeForCompare(line);
        if (StringUtils.isBlank(normalizedLine)) {
            return false;
        }
        String normFull = normalizeForCompare(fullTitle);
        String normBare = normalizeForCompare(bareTitle);
        if (normalizedLine.equals(normFull) || normalizedLine.equals(normBare)) {
            return true;
        }
        // 「2.1 Java简介」与「Java简介」
        if (StringUtils.isNotBlank(normBare) && normalizedLine.endsWith(normBare)) {
            String prefix = normalizedLine.substring(0, normalizedLine.length() - normBare.length());
            if (prefix.isEmpty() || prefix.matches("\\d+(\\.\\d+)*")) {
                return true;
            }
        }
        // 仅 Markdown 标题且文本与 bare 一致
        if (MARKDOWN_HEADING.matcher(line.trim()).find() && normalizedLine.equals(normBare)) {
            return true;
        }
        return false;
    }

    private static String normalizeForCompare(String text) {
        if (text == null) {
            return "";
        }
        String value = MARKDOWN_HEADING.matcher(text.trim()).replaceFirst("");
        value = value.replaceAll("^第[一二三四五六七八九十百千\\d]+[章节节]\\s*", "");
        value = value.replaceAll("[\\s、，,．.：:]", "");
        return value.toLowerCase(Locale.ROOT);
    }

    private static String stripNumericPrefix(String title) {
        if (title == null) {
            return "";
        }
        var m = NUMERIC_PREFIX.matcher(title.trim());
        return m.find() ? title.substring(m.end()).trim() : title.trim();
    }
}
