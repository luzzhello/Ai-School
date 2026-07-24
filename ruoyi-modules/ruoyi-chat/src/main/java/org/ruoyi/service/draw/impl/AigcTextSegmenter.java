package org.ruoyi.service.draw.impl;

import org.ruoyi.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 将论文文本按段或按句切分
 */
final class AigcTextSegmenter {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？!?；;])");

    private AigcTextSegmenter() {
    }

    static List<String> split(String content, String splitMode) {
        if (StringUtils.isBlank(content)) {
            return List.of();
        }
        List<String> raw;
        if ("paragraph".equals(splitMode)) {
            raw = splitParagraphs(content);
        }
        else if ("outline".equals(splitMode)) {
            raw = AigcOutlineSegmenter.splitFromText(content).stream()
                .map(AigcOutlineSegmenter.OutlinePart::segmentText)
                .toList();
        }
        else {
            raw = splitSentences(content);
        }
        List<String> segments = new ArrayList<>();
        for (String item : raw) {
            String text = StringUtils.trim(item);
            if (StringUtils.isNotBlank(text)) {
                segments.add(text);
            }
        }
        return segments.isEmpty() ? List.of(content.trim()) : segments;
    }

    private static List<String> splitParagraphs(String content) {
        String[] parts = content.split("\\n\\s*\\n+");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                list.add(part.trim());
            }
        }
        // 空行切分无效时（整篇连成一段），按单行拆——Word/PDF/粘贴正文常见
        if (list.size() <= 1 && StringUtils.isNotBlank(content)) {
            List<String> byLine = new ArrayList<>();
            for (String line : content.split("\\n+")) {
                if (StringUtils.isNotBlank(line)) {
                    byLine.add(line.trim());
                }
            }
            if (byLine.size() > 1) {
                return byLine;
            }
        }
        return list;
    }

    private static List<String> splitSentences(String content) {
        String[] sentences = SENTENCE_SPLIT.split(content);
        List<String> list = new ArrayList<>();
        for (String sentence : sentences) {
            if (StringUtils.isNotBlank(sentence)) {
                list.add(sentence.trim());
            }
        }
        return list;
    }

    static int countWords(String text) {
        return text == null ? 0 : text.replaceAll("\\s+", "").length();
    }

    /**
     * 将段落合并为适合检测的块：目标长度 [minChars, maxChars]，且不超过 maxChunks。
     */
    static List<String> mergeChunks(List<String> paragraphs, int minChars, int maxChars, int maxChunks) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return List.of();
        }
        List<String> merged = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String paragraph : paragraphs) {
            String text = StringUtils.trim(paragraph);
            if (StringUtils.isBlank(text)) {
                continue;
            }
            if (buf.isEmpty()) {
                buf.append(text);
                continue;
            }
            int nextLen = countWords(buf.toString()) + countWords(text);
            if (countWords(buf.toString()) < minChars || nextLen <= maxChars) {
                buf.append("\n\n").append(text);
            }
            else {
                merged.add(buf.toString());
                buf.setLength(0);
                buf.append(text);
            }
        }
        if (!buf.isEmpty()) {
            merged.add(buf.toString());
        }

        while (merged.size() > maxChunks) {
            List<String> compacted = new ArrayList<>();
            for (int i = 0; i < merged.size(); i += 2) {
                if (i + 1 < merged.size()) {
                    compacted.add(merged.get(i) + "\n\n" + merged.get(i + 1));
                }
                else {
                    compacted.add(merged.get(i));
                }
            }
            merged = compacted;
        }
        return merged;
    }
}
