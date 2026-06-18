package org.ruoyi.service.draw.impl;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.ruoyi.common.core.utils.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按论文章节 / 目录标题切分（每个目录项 = 可独立操作的一段）
 */
final class AigcOutlineSegmenter {

    /** 5.1.1 商品信息、5．1．1 商品信息 */
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
        "^(\\d+(?:[.．]\\d+)*)\\s*(.+)$");
    /** 第五章、第5章 */
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
        "^第[一二三四五六七八九十百零\\d]+章\\s*.+");
    /** 一、引言 */
    private static final Pattern CN_SECTION_HEADING = Pattern.compile(
        "^[一二三四五六七八九十百零]+[、．.\\s]\\s*\\S.+");

    private AigcOutlineSegmenter() {
    }

    record OutlinePart(String title, int level, String body) {

        String fullText() {
            if (StringUtils.isBlank(body)) {
                return title;
            }
            return title + "\n\n" + body;
        }

        /** 列表展示 / 计数字段：标题单独展示，此处仅正文 */
        String segmentText() {
            return StringUtils.isNotBlank(body) ? body : title;
        }
    }

    static List<OutlinePart> splitFromDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<OutlinePart> parts = new ArrayList<>();
            DocxSectionAccumulator accumulator = new DocxSectionAccumulator(parts);
            DocxBodyTraverser.traverse(document, accumulator::onParagraph, accumulator::onTableRow);
            accumulator.finish();
            parts = normalizeParts(parts);

            if (parts.size() <= 1) {
                List<FlatBlock> blocks = new ArrayList<>();
                DocxBodyTraverser.traverse(document,
                    (paragraph, text) -> blocks.add(new FlatBlock(paragraph, text)),
                    text -> blocks.add(new FlatBlock(null, text)));
                List<OutlinePart> retry = splitFromFlatBlocks(blocks);
                if (retry.size() > parts.size()) {
                    parts = normalizeParts(retry);
                }
            }
            return parts;
        }
    }

    static List<OutlinePart> splitFromText(String content) {
        if (StringUtils.isBlank(content)) {
            return List.of();
        }
        List<FlatBlock> blocks = new ArrayList<>();
        for (String rawLine : content.split("\\n")) {
            String line = StringUtils.trim(rawLine);
            if (StringUtils.isNotBlank(line)) {
                blocks.add(new FlatBlock(null, line));
            }
        }
        return normalizeParts(splitFromFlatBlocks(blocks));
    }

    private record FlatBlock(XWPFParagraph paragraph, String text) {
    }

    private static List<OutlinePart> splitFromFlatBlocks(List<FlatBlock> blocks) {
        List<OutlinePart> parts = new ArrayList<>();
        OutlinePart current = null;
        StringBuilder body = new StringBuilder();

        for (FlatBlock block : blocks) {
            String text = StringUtils.trim(block.text());
            if (StringUtils.isBlank(text)) {
                continue;
            }
            int headingLevel = resolveHeadingLevel(block.paragraph(), text);
            if (headingLevel > 0) {
                if (current != null) {
                    parts.add(new OutlinePart(current.title(), current.level(), body.toString().trim()));
                }
                current = new OutlinePart(text, headingLevel, "");
                body = new StringBuilder();
                continue;
            }
            if (current == null) {
                current = new OutlinePart("正文", 0, "");
            }
            if (body.length() > 0) {
                body.append("\n\n");
            }
            body.append(text);
        }
        if (current != null) {
            parts.add(new OutlinePart(current.title(), current.level(), body.toString().trim()));
        }
        return parts;
    }

    private static final class DocxSectionAccumulator {

        private final List<OutlinePart> parts;
        private OutlinePart current;
        private final StringBuilder body = new StringBuilder();

        private DocxSectionAccumulator(List<OutlinePart> parts) {
            this.parts = parts;
        }

        void onParagraph(XWPFParagraph paragraph, String text) {
            int headingLevel = resolveHeadingLevel(paragraph, text);
            if (headingLevel > 0) {
                flush();
                current = new OutlinePart(text, headingLevel, "");
                return;
            }
            ensureCurrent();
            appendBody(text);
        }

        void onTableRow(String rowText) {
            ensureCurrent();
            appendBody(rowText);
        }

        void finish() {
            flush();
        }

        private void ensureCurrent() {
            if (current == null) {
                current = new OutlinePart("正文", 0, "");
            }
        }

        private void appendBody(String text) {
            if (body.length() > 0) {
                body.append("\n\n");
            }
            body.append(text);
        }

        private void flush() {
            if (current == null) {
                return;
            }
            parts.add(new OutlinePart(current.title(), current.level(), body.toString().trim()));
            current = null;
            body.setLength(0);
        }
    }

    private static List<OutlinePart> normalizeParts(List<OutlinePart> parts) {
        List<OutlinePart> exploded = explodeNestedHeadings(parts);
        List<OutlinePart> normalized = new ArrayList<>();
        for (OutlinePart part : exploded) {
            String full = part.fullText();
            if (StringUtils.isBlank(full)) {
                continue;
            }
            normalized.add(part);
        }
        return normalized.isEmpty() ? List.of(new OutlinePart("全文", 0, "")) : normalized;
    }

    /** 大段正文中若夹杂 5.1.1 这类标题行，继续拆成独立目录段 */
    private static List<OutlinePart> explodeNestedHeadings(List<OutlinePart> parts) {
        List<OutlinePart> result = new ArrayList<>();
        for (OutlinePart part : parts) {
            result.addAll(explodeSinglePart(part));
        }
        return result;
    }

    private static List<OutlinePart> explodeSinglePart(OutlinePart part) {
        if (StringUtils.isBlank(part.body())) {
            return List.of(part);
        }
        String[] blocks = part.body().split("\\n\\s*\\n");
        List<OutlinePart> sub = new ArrayList<>();
        String currentTitle = part.title();
        int currentLevel = part.level();
        StringBuilder body = new StringBuilder();
        boolean foundNested = false;

        for (String raw : blocks) {
            String text = StringUtils.trim(raw);
            if (StringUtils.isBlank(text)) {
                continue;
            }
            int headingLevel = resolveHeadingLevelFromText(text);
            if (headingLevel > 0) {
                foundNested = true;
                if (body.length() > 0 || !sub.isEmpty()) {
                    sub.add(new OutlinePart(currentTitle, currentLevel, body.toString().trim()));
                    body = new StringBuilder();
                }
                else if (body.length() == 0 && sub.isEmpty()) {
                    // 父标题下无引言，直接进入子节
                }
                currentTitle = text;
                currentLevel = headingLevel;
                continue;
            }
            if (body.length() > 0) {
                body.append("\n\n");
            }
            body.append(text);
        }
        sub.add(new OutlinePart(currentTitle, currentLevel, body.toString().trim()));
        return foundNested ? sub : List.of(part);
    }

    private static int resolveHeadingLevel(XWPFParagraph paragraph, String text) {
        if (paragraph != null) {
            int fromStyle = headingLevelFromStyle(paragraph);
            if (fromStyle > 0) {
                return fromStyle;
            }
            int fromNumbering = headingLevelFromWordNumbering(paragraph, text);
            if (fromNumbering > 0) {
                return fromNumbering;
            }
            int fromBold = headingLevelFromBold(paragraph, text);
            if (fromBold > 0) {
                return fromBold;
            }
        }
        return resolveHeadingLevelFromText(text);
    }

    private static int headingLevelFromStyle(XWPFParagraph paragraph) {
        String styleId = StringUtils.defaultString(paragraph.getStyleID());
        String style = StringUtils.defaultString(paragraph.getStyle());
        int level = parseHeadingLevelToken(styleId);
        if (level > 0) {
            return level;
        }
        level = parseHeadingLevelToken(style);
        if (level > 0) {
            return level;
        }
        if (paragraph.getCTP().getPPr() != null && paragraph.getCTP().getPPr().isSetOutlineLvl()) {
            return paragraph.getCTP().getPPr().getOutlineLvl().getVal().intValue() + 1;
        }
        return 0;
    }

    private static int headingLevelFromWordNumbering(XWPFParagraph paragraph, String text) {
        BigInteger numId = paragraph.getNumID();
        if (numId == null || numId.intValue() <= 0) {
            return 0;
        }
        if (looksLikeBodySentence(text)) {
            return 0;
        }
        BigInteger ilvl = paragraph.getNumIlvl();
        return (ilvl != null ? ilvl.intValue() : 0) + 1;
    }

    private static int headingLevelFromBold(XWPFParagraph paragraph, String text) {
        if (looksLikeBodySentence(text)) {
            return 0;
        }
        if (!isBoldDominant(paragraph)) {
            return 0;
        }
        int fromText = resolveHeadingLevelFromText(text);
        if (fromText > 0) {
            return fromText;
        }
        return text.length() <= 60 ? 2 : 0;
    }

    private static boolean isBoldDominant(XWPFParagraph paragraph) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return false;
        }
        int boldChars = 0;
        int totalChars = 0;
        for (XWPFRun run : runs) {
            String runText = run.text();
            if (StringUtils.isBlank(runText)) {
                continue;
            }
            int len = runText.length();
            totalChars += len;
            if (run.isBold()) {
                boldChars += len;
            }
        }
        return totalChars > 0 && boldChars * 2 >= totalChars;
    }

    private static boolean looksLikeBodySentence(String text) {
        String trimmed = StringUtils.trim(text);
        if (trimmed.length() > 250) {
            return true;
        }
        return trimmed.matches(".*[。；;！？!?]$");
    }

    private static int parseHeadingLevelToken(String token) {
        if (StringUtils.isBlank(token)) {
            return 0;
        }
        String lower = token.toLowerCase();
        if (lower.contains("heading") || lower.contains("标题")) {
            Matcher matcher = Pattern.compile("(\\d+)").matcher(lower);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            return 1;
        }
        return 0;
    }

    private static int resolveHeadingLevelFromText(String line) {
        String normalized = normalizeHeadingLine(line);
        if (CHAPTER_HEADING.matcher(normalized).matches()) {
            return 1;
        }
        if (CN_SECTION_HEADING.matcher(normalized).matches() && normalized.length() <= 80) {
            return 1;
        }
        Matcher matcher = NUMBERED_HEADING.matcher(normalized);
        if (!matcher.matches()) {
            return 0;
        }
        String number = matcher.group(1).replace('．', '.');
        if (normalized.length() > 120) {
            return 0;
        }
        if (looksLikeBodySentence(normalized)) {
            return 0;
        }
        int dots = number.length() - number.replace(".", "").length();
        return dots + 1;
    }

    private static String normalizeHeadingLine(String line) {
        return StringUtils.trim(line)
            .replace('\u00A0', ' ')
            .replaceAll("[ \\t]+", " ");
    }
}
