package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperUiScreenshotImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将第五章功能界面截图占位替换为多张 Markdown 图（列表/新增/详情等）。
 * 图注格式对齐论文规范：{@code 图 5.2 骑行活动列表界面}。
 */
public final class PaperUiScreenshotInjector {

    private static final Pattern[] PLACEHOLDER_PATTERNS = {
        Pattern.compile("【此处插入[^】]*界面截图[^】]*】"),
        Pattern.compile("\\[此处插入[^\\]]*界面截图[^\\]]*\\]"),
        Pattern.compile("【此处插入[^】]*功能界面截图[^】]*】"),
        Pattern.compile("\\[此处插入[^\\]]*功能界面截图[^\\]]*\\]"),
    };

    private static final Pattern CHAPTER_NO = Pattern.compile("^(\\d+)");
    /** 任意 Markdown 图片（含行内） */
    private static final Pattern ANY_MARKDOWN_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\(([^)]+)\\)");
    private static final Pattern CJK_GAP = Pattern.compile("(?<=[\\u4e00-\\u9fff])\\s+(?=[\\u4e00-\\u9fff])");

    private PaperUiScreenshotInjector() {
    }

    /**
     * 将行内/粘连的 Markdown 图片提升为独立块，并规范化 {@code //api/...} 路径。
     * Word 导出仅识别整行图片，此规范化同时服务预览与导出。
     */
    public static String normalizeMarkdownImagesAsBlocks(String content) {
        if (StringUtils.isBlank(content)) {
            return content == null ? "" : content;
        }
        String text = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!ANY_MARKDOWN_IMAGE.matcher(text).find()) {
            return text;
        }
        Matcher matcher = ANY_MARKDOWN_IMAGE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String caption = collapseCaptionSpaces(matcher.group(1) == null ? "" : matcher.group(1).trim());
            String src = matcher.group(2) == null ? "" : matcher.group(2).trim();
            if (src.startsWith("//")) {
                src = src.substring(1);
            }
            String block = "\n\n![" + caption + "](" + src + ")\n\n";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(block));
        }
        matcher.appendTail(sb);
        return sb.toString()
            .replaceAll("[ \\t]+\\n", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .replaceAll("^\\n+", "")
            .replaceAll("\\n+$", "\n");
    }

    private static String collapseCaptionSpaces(String caption) {
        if (StringUtils.isBlank(caption)) {
            return "";
        }
        return CJK_GAP.matcher(caption).replaceAll("").replaceAll("\\s{2,}", " ").trim();
    }

    /**
     * 注入多张截图：优先逐个替换占位；占位不足时把剩余图追加在文末。
     * 图序号从 {@code startFigureIndex} 起连续递增。
     */
    public static String injectAll(
        String content,
        List<PaperUiScreenshotImage> images,
        String featureTitle,
        int chapterNo,
        int startFigureIndex
    ) {
        if (StringUtils.isBlank(content) || images == null || images.isEmpty()) {
            return content;
        }
        int chapter = chapterNo > 0 ? chapterNo : 5;
        int figureIndex = Math.max(1, startFigureIndex);
        List<String> markdowns = new ArrayList<>();
        for (PaperUiScreenshotImage image : images) {
            if (image == null || StringUtils.isBlank(image.getAssetUrl())) {
                continue;
            }
            String caption = buildCaption(featureTitle, image.getLabel(), chapter, figureIndex++);
            String src = image.getAssetUrl().trim();
            if (src.startsWith("//")) {
                src = src.substring(1);
            }
            markdowns.add("![" + caption + "](" + src + ")");
        }
        if (markdowns.isEmpty()) {
            return content;
        }

        String result = content;
        int replaced = 0;
        for (Pattern pattern : PLACEHOLDER_PATTERNS) {
            Matcher matcher = pattern.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (matcher.find() && replaced < markdowns.size()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("\n\n" + markdowns.get(replaced) + "\n\n"));
                replaced++;
            }
            matcher.appendTail(sb);
            result = sb.toString();
            if (replaced >= markdowns.size()) {
                return normalizeMarkdownImagesAsBlocks(result);
            }
        }

        if (replaced == 0) {
            result = result + "\n\n" + String.join("\n\n", markdowns) + "\n";
        } else if (replaced < markdowns.size()) {
            String rest = String.join("\n\n", markdowns.subList(replaced, markdowns.size()));
            result = result + "\n\n" + rest + "\n";
        }
        return normalizeMarkdownImagesAsBlocks(result);
    }

    /** 兼容旧调用：默认第 5 章、从图 5.1 起编 */
    public static String injectAll(String content, List<PaperUiScreenshotImage> images, String featureTitle) {
        return injectAll(content, images, featureTitle, 5, 1);
    }

    /** 兼容旧单图调用 */
    public static String inject(String content, String assetUrl, String caption) {
        if (StringUtils.isBlank(assetUrl)) {
            return content;
        }
        PaperUiScreenshotImage image = new PaperUiScreenshotImage();
        image.setAssetUrl(assetUrl);
        image.setLabel(null);
        return injectAll(content, List.of(image), caption);
    }

    /**
     * @param featureTitle 功能名（可含章节编号，会剥除）
     * @param label        界面类型：列表/新增/编辑/详情/其他
     * @param chapterNo    章号，如 5
     * @param figureIndex  章内图序，如 2 → {@code 图 5.2 …}
     */
    static String buildCaption(String featureTitle, String label, int chapterNo, int figureIndex) {
        String feature = bareFeatureTitle(featureTitle);
        String lab = StringUtils.isNotBlank(label) ? label.trim() : "";
        String desc;
        if (StringUtils.isBlank(lab) || "其他".equals(lab)) {
            desc = feature + "界面";
        } else {
            desc = feature + lab + "界面";
        }
        int ch = chapterNo > 0 ? chapterNo : 5;
        int idx = Math.max(1, figureIndex);
        return "图 " + ch + "." + idx + " " + desc;
    }

    /** 兼容旧测试：无序号时仍返回描述（已废弃路径） */
    static String buildCaption(String featureTitle, String label) {
        return buildCaption(featureTitle, label, 5, 1);
    }

    static String bareFeatureTitle(String featureTitle) {
        String feature = StringUtils.isNotBlank(featureTitle) ? featureTitle.trim() : "功能";
        feature = feature.replaceFirst("^[0-9]+(?:\\.[0-9]+)*\\s*", "").trim();
        feature = feature.replaceAll("功能$", "");
        if (StringUtils.isBlank(feature)) {
            feature = "功能";
        }
        return feature;
    }

    /** 从「5.1.1 骑行活动管理功能」提取章号 5；失败默认 5。 */
    static int extractChapterNo(String chapterTitle) {
        if (StringUtils.isBlank(chapterTitle)) {
            return 5;
        }
        Matcher m = CHAPTER_NO.matcher(chapterTitle.trim());
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return 5;
            }
        }
        return 5;
    }

    /**
     * 扫描已有正文中「图 5.n」的最大 n，返回下一序号。
     */
    static int nextFigureIndex(int chapterNo, Map<String, String> generatedContent, String currentContent) {
        int ch = chapterNo > 0 ? chapterNo : 5;
        Pattern p = Pattern.compile("图\\s*" + ch + "\\.(\\d+)");
        int max = 0;
        if (generatedContent != null) {
            for (String body : generatedContent.values()) {
                max = Math.max(max, maxFigureIndexIn(body, p));
            }
        }
        max = Math.max(max, maxFigureIndexIn(currentContent, p));
        return max + 1;
    }

    private static int maxFigureIndexIn(String body, Pattern p) {
        if (StringUtils.isBlank(body)) {
            return 0;
        }
        int max = 0;
        Matcher m = p.matcher(body);
        while (m.find()) {
            try {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return max;
    }
}
