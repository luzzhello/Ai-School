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
        String[] parts = content.split("\\n\\s*\\n");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                list.add(part.trim());
            }
        }
        if (list.isEmpty() && StringUtils.isNotBlank(content)) {
            for (String line : content.split("\\n")) {
                if (StringUtils.isNotBlank(line)) {
                    list.add(line.trim());
                }
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
}
