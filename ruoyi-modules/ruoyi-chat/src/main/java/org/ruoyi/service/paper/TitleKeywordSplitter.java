package org.ruoyi.service.paper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将论文标题拆分为少量检索关键词。
 */
public final class TitleKeywordSplitter {

    private static final Pattern TITLE_MARKS = Pattern.compile("[《》〈〉「」『』【】〔〕]");
    private static final Pattern ENGLISH_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9.+#-]*");
    private static final Pattern CHINESE_FRAGMENT = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final Pattern STOPWORD = Pattern.compile(
        "基于|面向|研究|设计|实现|系统|平台|分析|应用|开发|一种|与|及|和|的");

    private TitleKeywordSplitter() {
    }

    public static List<String> split(String title, int minKeywords, int maxKeywords) {
        if (title == null || maxKeywords <= 0) {
            return List.of();
        }

        String cleanedTitle = TITLE_MARKS.matcher(title.trim()).replaceAll("");
        if (cleanedTitle.isEmpty()) {
            return List.of();
        }

        Set<String> keywords = new LinkedHashSet<>();
        Matcher englishMatcher = ENGLISH_TOKEN.matcher(cleanedTitle);
        while (englishMatcher.find()) {
            keywords.add(englishMatcher.group());
        }

        String chineseText = ENGLISH_TOKEN.matcher(cleanedTitle).replaceAll(" ");
        chineseText = STOPWORD.matcher(chineseText).replaceAll(" ");
        Matcher chineseMatcher = CHINESE_FRAGMENT.matcher(chineseText);
        while (chineseMatcher.find()) {
            String fragment = chineseMatcher.group();
            if (fragment.length() >= 2 && fragment.length() <= 8) {
                keywords.add(fragment);
            }
        }

        List<String> result = new ArrayList<>(keywords);
        if (result.size() > maxKeywords) {
            return new ArrayList<>(result.subList(0, maxKeywords));
        }

        String fallback = cleanedTitle.substring(0, Math.min(20, cleanedTitle.length()));
        int requestedMinimum = Math.min(Math.max(minKeywords, 0), maxKeywords);
        if (result.size() < requestedMinimum && !result.contains(fallback)) {
            result.add(fallback);
        }
        // 只有一个有意义的整题补位词；无法继续唯一补位时允许少于 requestedMinimum。
        return result;
    }
}
