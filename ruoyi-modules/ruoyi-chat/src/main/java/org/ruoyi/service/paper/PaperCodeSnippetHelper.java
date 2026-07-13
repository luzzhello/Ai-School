package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从上传的项目代码中提取与当前章节相关的片段（支持 zip 合并后的多文件文本）。
 */
final class PaperCodeSnippetHelper {

    private static final int MAX_LEN = 8000;
    private static final Pattern FILE_BLOCK = Pattern.compile("// ===== (.+?) =====\\r?\\n([\\s\\S]*?)(?=\\r?\\n// ===== |\\z)");
    private static final Pattern TITLE_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s+(.+)$");

    private PaperCodeSnippetHelper() {
    }

    static String snippet(String code, String chapterTitle) {
        if (StringUtils.isBlank(code)) {
            return "（未上传项目代码，请根据数据库表与功能模块推断实现逻辑）";
        }
        code = PaperJavaSourceCleaner.cleanProjectCode(code);
        if (code.contains("// ===== ") && StringUtils.isNotBlank(chapterTitle)) {
            String matched = extractMatchedBlocks(code, chapterTitle);
            if (StringUtils.isNotBlank(matched)) {
                return truncate(matched, MAX_LEN);
            }
            String prioritized = extractPriorityBlocks(code);
            if (StringUtils.isNotBlank(prioritized)) {
                return truncate(prioritized, MAX_LEN);
            }
        }
        return truncate(code, MAX_LEN);
    }

    private static String extractMatchedBlocks(String code, String chapterTitle) {
        List<String> keywords = moduleKeywords(chapterTitle);
        if (keywords.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Matcher matcher = FILE_BLOCK.matcher(code);
        while (matcher.find()) {
            String path = matcher.group(1);
            String body = matcher.group(2);
            if (matchesKeywords(path, body, keywords)) {
                sb.append("\n// ===== ").append(path).append(" =====\n").append(body.trim()).append('\n');
            }
        }
        return sb.length() == 0 ? null : sb.toString().trim();
    }

    private static String extractPriorityBlocks(String code) {
        List<Block> blocks = new ArrayList<>();
        Matcher matcher = FILE_BLOCK.matcher(code);
        while (matcher.find()) {
            blocks.add(new Block(matcher.group(1), matcher.group(2).trim(), scoreJavaPath(matcher.group(1))));
        }
        blocks.sort((a, b) -> Integer.compare(b.score, a.score));
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            sb.append("\n// ===== ").append(block.path).append(" =====\n").append(block.body).append('\n');
        }
        return sb.toString().trim();
    }

    private static boolean matchesKeywords(String path, String body, List<String> keywords) {
        String corpus = (path + '\n' + body).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (corpus.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> moduleKeywords(String chapterTitle) {
        Set<String> keywords = new LinkedHashSet<>();
        String title = chapterTitle == null ? "" : chapterTitle.trim();
        Matcher matcher = TITLE_PREFIX.matcher(title);
        String bare = matcher.matches() ? matcher.group(2).trim() : title;
        bare = bare.replace("模块实现", "")
            .replace("功能模块", "")
            .replace("管理功能", "管理")
            .replace("功能", "")
            .replace("模块", "")
            .replace("实现", "")
            .trim();

        if (StringUtils.isNotBlank(bare)) {
            keywords.add(bare);
            for (String part : bare.split("[\\s、，,/]+")) {
                if (StringUtils.isNotBlank(part)) {
                    keywords.add(part.trim());
                }
            }
        }

        addPinyinLikeKeywords(keywords, bare);
        return new ArrayList<>(keywords);
    }

    private static void addPinyinLikeKeywords(Set<String> keywords, String bare) {
        if (StringUtils.isBlank(bare)) {
            return;
        }
        if (bare.contains("用户")) {
            keywords.add("user");
            keywords.add("User");
        }
        if (bare.contains("订单")) {
            keywords.add("order");
            keywords.add("Order");
        }
        if (bare.contains("菜单")) {
            keywords.add("menu");
            keywords.add("Menu");
        }
        if (bare.contains("角色")) {
            keywords.add("role");
            keywords.add("Role");
        }
        if (bare.contains("图书")) {
            keywords.add("book");
            keywords.add("Book");
        }
        if (bare.contains("借阅")) {
            keywords.add("borrow");
            keywords.add("Borrow");
        }
        if (bare.contains("骑行") || bare.contains("路线")) {
            keywords.add("ride");
            keywords.add("route");
            keywords.add("Riding");
            keywords.add("Route");
        }
        if (bare.contains("预约") || bare.contains("座位")) {
            keywords.add("reservation");
            keywords.add("seat");
            keywords.add("appointment");
        }
        if (bare.contains("资讯") || bare.contains("新闻") || bare.contains("文章")) {
            keywords.add("news");
            keywords.add("article");
            keywords.add("notice");
        }
    }

    private static int scoreJavaPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("controller")) {
            return 100;
        }
        if (lower.contains("serviceimpl")) {
            return 92;
        }
        if (lower.contains("service")) {
            return 85;
        }
        if (lower.contains("mapper")) {
            return 72;
        }
        return 10;
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "\n...（代码已截断，建议 zip 中仅保留核心 Controller/Service 文件）";
    }

    private record Block(String path, String body, int score) {
    }
}
