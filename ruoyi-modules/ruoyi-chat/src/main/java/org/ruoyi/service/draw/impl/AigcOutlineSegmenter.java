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
 * 按论文章节 / 目录标题切分。
 * 规则：每个标题（含其子级标题）各自开启新段，标题下正文互不合并。
 */
final class AigcOutlineSegmenter {

    /** 5.1.1 商品信息、5．1．1 商品信息 */
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
        "^(\\d+(?:[.．]\\d+)*)\\s*(.+)$");
    /** 第五章、第5章 */
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
        "^第[一二三四五六七八九十百零\\d]+章(?:\\s*.+)?$");
    /** 一、引言 */
    private static final Pattern CN_SECTION_HEADING = Pattern.compile(
        "^[一二三四五六七八九十百零]+[、．.]\\s*\\S.+");
    /** 从编号标题行里切开「标题 + 粘连正文」 */
    private static final Pattern NUMBERED_HEADING_CUT = Pattern.compile(
        "^(\\d+(?:[.．]\\d+)*)\\s+(\\S.{0,60}?)(?:\\s{2,}|[：:]\\s*|\\s+)(.+)$");
    /**
     * 论文固定板块（无编号时也要单独成段）：
     * 摘要 / 致谢 / 参考文献 / 附录 / 总结 / 结论 等
     */
    private static final Pattern SPECIAL_SECTION = Pattern.compile(
        "^(?:"
            + "摘要|Abstract|关键词|目录|"
            + "致谢|鸣谢|"
            + "参考文献|参考资料|"
            + "附录[A-Za-z0-9一二三四五六七八九十]*\\s*.{0,40}|"
            + "总结(?:与展望|与建议)?|结论(?:与展望|与建议)?|结语|结束语|"
            + "攻读学位期间.{0,30}"
            + ")$",
        Pattern.CASE_INSENSITIVE);

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
        return normalizeParts(splitFromFlatBlocks(toFlatBlocks(content)));
    }

    /**
     * AIGC 检测专用切分：每个标题下的正文作为一段（一/二/三级标题各自独立），
     * 不把同一标题下的自然段再拆开；目录、参考文献等排除，标题行本身不进入检测。
     */
    static List<OutlinePart> splitForDetect(String content) {
        List<OutlinePart> result = new ArrayList<>();
        for (OutlinePart part : splitOutlineUnits(content)) {
            if (isDetectableBody(part)) {
                result.add(part);
            }
        }
        return result;
    }

    /** 全文大纲单元（含目录/参考文献等），用于连续原文展示 */
    static List<OutlinePart> splitOutlineUnits(String content) {
        if (StringUtils.isBlank(content)) {
            return List.of();
        }
        List<OutlinePart> units = normalizeOutlineUnits(splitFromFlatBlocks(toFlatBlocks(content)));
        if (units.size() == 1 && "全文".equals(units.get(0).title()) && StringUtils.isBlank(units.get(0).body())) {
            return List.of();
        }
        return units;
    }

    static boolean isDetectableBody(OutlinePart part) {
        if (part == null) {
            return false;
        }
        if (shouldSkipDetectSection(part.title())) {
            return false;
        }
        return StringUtils.isNotBlank(part.body());
    }

    private static List<FlatBlock> toFlatBlocks(String content) {
        String normalized = content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u2028', '\n')
            .replace('\u2029', '\n');
        List<FlatBlock> blocks = new ArrayList<>();
        for (String rawLine : normalized.split("\n")) {
            String line = StringUtils.trim(rawLine);
            if (StringUtils.isBlank(line)) {
                continue;
            }
            // 一行内「标题 + 正文」粘连时先拆开，避免多个小节挤进同一段
            HeadingCut cut = cutLeadingHeading(line);
            if (cut != null) {
                blocks.add(new FlatBlock(null, cut.title()));
                if (StringUtils.isNotBlank(cut.body())) {
                    blocks.add(new FlatBlock(null, cut.body()));
                }
            }
            else {
                blocks.add(new FlatBlock(null, line));
            }
        }
        return blocks;
    }

    private record FlatBlock(XWPFParagraph paragraph, String text) {
    }

    private record HeadingCut(String title, int level, String body) {
    }

    private static List<OutlinePart> splitFromFlatBlocks(List<FlatBlock> blocks) {
        List<OutlinePart> parts = new ArrayList<>();
        String currentTitle = null;
        int currentLevel = 0;
        StringBuilder body = new StringBuilder();

        for (FlatBlock block : blocks) {
            String text = StringUtils.trim(block.text());
            if (StringUtils.isBlank(text)) {
                continue;
            }
            // 非纯文本块：优先用 Word 样式；纯文本再尝试粘连切开
            int headingLevel = resolveHeadingLevel(block.paragraph(), text);
            String headingTitle = text;
            String trailingBody = null;
            if (headingLevel <= 0 && block.paragraph() == null) {
                HeadingCut cut = cutLeadingHeading(text);
                if (cut != null) {
                    headingLevel = cut.level();
                    headingTitle = cut.title();
                    trailingBody = cut.body();
                }
            }
            if (headingLevel > 0) {
                flushPart(parts, currentTitle, currentLevel, body);
                currentTitle = headingTitle;
                currentLevel = headingLevel;
                body = new StringBuilder();
                if (StringUtils.isNotBlank(trailingBody)) {
                    body.append(trailingBody.trim());
                }
                continue;
            }
            if (currentTitle == null) {
                currentTitle = "正文";
                currentLevel = 0;
            }
            if (body.length() > 0) {
                body.append("\n\n");
            }
            body.append(text);
        }
        flushPart(parts, currentTitle, currentLevel, body);
        return parts;
    }

    private static void flushPart(List<OutlinePart> parts, String title, int level, StringBuilder body) {
        if (title == null) {
            return;
        }
        parts.add(new OutlinePart(title, level, body.toString().trim()));
    }

    private static final class DocxSectionAccumulator {

        private final List<OutlinePart> parts;
        private String currentTitle;
        private int currentLevel;
        private final StringBuilder body = new StringBuilder();

        private DocxSectionAccumulator(List<OutlinePart> parts) {
            this.parts = parts;
        }

        void onParagraph(XWPFParagraph paragraph, String text) {
            int headingLevel = resolveHeadingLevel(paragraph, text);
            if (headingLevel > 0) {
                flush();
                currentTitle = text;
                currentLevel = headingLevel;
                return;
            }
            // Word 段落偶发「标题+正文」同行，再尝试切开
            HeadingCut cut = cutLeadingHeading(text);
            if (cut != null) {
                flush();
                currentTitle = cut.title();
                currentLevel = cut.level();
                if (StringUtils.isNotBlank(cut.body())) {
                    appendBody(cut.body());
                }
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
            if (currentTitle == null) {
                currentTitle = "正文";
                currentLevel = 0;
            }
        }

        private void appendBody(String text) {
            if (body.length() > 0) {
                body.append("\n\n");
            }
            body.append(text);
        }

        private void flush() {
            if (currentTitle == null) {
                return;
            }
            parts.add(new OutlinePart(currentTitle, currentLevel, body.toString().trim()));
            currentTitle = null;
            currentLevel = 0;
            body.setLength(0);
        }
    }

    private static List<OutlinePart> normalizeParts(List<OutlinePart> parts) {
        // 降 AIGC：标题下正文再按自然段拆开，便于逐段改写
        List<OutlinePart> byParagraph = splitBodiesByParagraph(normalizeOutlineUnits(parts));
        List<OutlinePart> normalized = new ArrayList<>();
        for (OutlinePart part : byParagraph) {
            if (StringUtils.isBlank(part.fullText())) {
                continue;
            }
            normalized.add(part);
        }
        return normalized.isEmpty() ? List.of(new OutlinePart("全文", 0, "")) : normalized;
    }

    /** 仅按标题层级切段（一标题一段正文），不再按自然段拆开 */
    private static List<OutlinePart> normalizeOutlineUnits(List<OutlinePart> parts) {
        List<OutlinePart> exploded = explodeNestedHeadings(parts);
        List<OutlinePart> normalized = new ArrayList<>();
        for (OutlinePart part : exploded) {
            if (StringUtils.isBlank(part.fullText())) {
                continue;
            }
            normalized.add(part);
        }
        return normalized.isEmpty() ? List.of(new OutlinePart("全文", 0, "")) : normalized;
    }

    /**
     * 不进入检测：目录、参考文献（引入的文献）等。
     * 摘要/致谢/总结等仍检测其正文。
     */
    static boolean shouldSkipDetectSection(String title) {
        String normalized = normalizeHeadingLine(title);
        if (StringUtils.isBlank(normalized)) {
            return true;
        }
        String core = normalized
            .replaceAll("[（(].*$", "")
            .replaceAll("\\s+", "")
            .trim();
        String lower = core.toLowerCase();
        if (lower.equals("目录") || lower.equals("contents") || lower.equals("tableofcontents")
            || lower.startsWith("目录")) {
            return true;
        }
        if (lower.contains("参考文献") || lower.contains("参考资料")
            || lower.equals("references") || lower.equals("bibliography")
            || lower.equals("works cited") || lower.equals("literature cited")) {
            return true;
        }
        // 纯「关键词」列表通常不是正文论述
        if (lower.equals("关键词") || lower.equals("关键字") || lower.equals("keywords")) {
            return true;
        }
        return false;
    }

    /**
     * 同一标题下若有多段正文，拆成多条（标题相同，正文各一段）。
     */
    private static List<OutlinePart> splitBodiesByParagraph(List<OutlinePart> parts) {
        List<OutlinePart> result = new ArrayList<>();
        for (OutlinePart part : parts) {
            if (StringUtils.isBlank(part.body())) {
                result.add(part);
                continue;
            }
            List<String> paragraphs = splitParagraphBlocks(part.body());
            if (paragraphs.size() <= 1) {
                result.add(part);
                continue;
            }
            for (String paragraph : paragraphs) {
                result.add(new OutlinePart(part.title(), part.level(), paragraph));
            }
        }
        return result;
    }

    private static List<String> splitParagraphBlocks(String body) {
        String[] raw = body.split("\\n\\s*\\n+");
        List<String> list = new ArrayList<>();
        for (String item : raw) {
            String text = StringUtils.trim(item);
            if (StringUtils.isNotBlank(text)) {
                list.add(text);
            }
        }
        // 若几乎没有空行分段，但存在多行且单行较短像独立段，再按单行拆
        if (list.size() <= 1) {
            String[] lines = body.split("\\n+");
            List<String> lineBlocks = new ArrayList<>();
            for (String line : lines) {
                String text = StringUtils.trim(line);
                if (StringUtils.isNotBlank(text)) {
                    lineBlocks.add(text);
                }
            }
            if (lineBlocks.size() > 1 && lineBlocks.stream().allMatch(s -> s.length() >= 40 || looksLikeBodySentence(s))) {
                return lineBlocks;
            }
        }
        return list;
    }

    /**
     * 大段正文中若仍夹杂标题行，继续拆成独立目录段，保证「一标题一段」。
     */
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
        // 按任意换行扫描，避免标题只被空行分隔时漏拆
        String[] lines = part.body().split("\\n+");
        List<OutlinePart> sub = new ArrayList<>();
        String currentTitle = part.title();
        int currentLevel = part.level();
        StringBuilder body = new StringBuilder();
        boolean foundNested = false;

        for (String raw : lines) {
            String text = StringUtils.trim(raw);
            if (StringUtils.isBlank(text)) {
                continue;
            }
            HeadingCut cut = cutLeadingHeading(text);
            int headingLevel = cut != null ? cut.level() : resolveHeadingLevelFromText(text);
            String headingTitle = cut != null ? cut.title() : text;
            String trailing = cut != null ? cut.body() : null;

            if (headingLevel > 0 && (cut != null || isStandaloneHeadingLine(text))) {
                foundNested = true;
                flushPart(sub, currentTitle, currentLevel, body);
                body = new StringBuilder();
                currentTitle = headingTitle;
                currentLevel = headingLevel;
                if (StringUtils.isNotBlank(trailing)) {
                    body.append(trailing.trim());
                }
                continue;
            }
            if (body.length() > 0) {
                body.append("\n\n");
            }
            body.append(text);
        }
        flushPart(sub, currentTitle, currentLevel, body);
        return foundNested ? sub : List.of(part);
    }

    /**
     * 识别「标题粘连正文」或独立标题行，切开为 title / body。
     */
    private static HeadingCut cutLeadingHeading(String text) {
        String normalized = normalizeHeadingLine(text);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        if (CHAPTER_HEADING.matcher(normalized).matches() && normalized.length() <= 80) {
            return new HeadingCut(normalized, 1, "");
        }
        if (isSpecialSectionTitle(normalized)) {
            return new HeadingCut(normalized, 1, "");
        }
        if (CN_SECTION_HEADING.matcher(normalized).matches() && normalized.length() <= 80
            && !looksLikeBodySentence(normalized)) {
            return new HeadingCut(normalized, 1, "");
        }

        if (isStandaloneHeadingLine(normalized)) {
            int level = resolveHeadingLevelFromText(normalized);
            if (level > 0) {
                return new HeadingCut(normalized, level, "");
            }
        }

        Matcher cut = NUMBERED_HEADING_CUT.matcher(normalized);
        if (cut.matches()) {
            String number = cut.group(1).replace('．', '.');
            String titleTail = StringUtils.trim(cut.group(2));
            String rest = StringUtils.trim(cut.group(3));
            if (StringUtils.isBlank(titleTail) || StringUtils.isBlank(rest)) {
                return null;
            }
            if (looksLikeBodySentence(titleTail) || titleTail.length() > 60) {
                return null;
            }
            int dots = number.length() - number.replace(".", "").length();
            String title = number + " " + titleTail;
            return new HeadingCut(title, dots + 1, rest);
        }

        // 宽松：编号 + 短标题 + 后续长文（无双空格时）
        Matcher numbered = NUMBERED_HEADING.matcher(normalized);
        if (numbered.matches()) {
            String number = numbered.group(1).replace('．', '.');
            String restAll = StringUtils.trim(numbered.group(2));
            if (restAll.length() <= 60 && !looksLikeBodySentence(restAll) && normalized.length() <= 100) {
                int dots = number.length() - number.replace(".", "").length();
                return new HeadingCut(normalized, dots + 1, "");
            }
            // 取前若干字作标题，其余当正文
            int splitAt = findTitleBodySplit(restAll);
            if (splitAt > 0 && splitAt < restAll.length()) {
                String titleTail = restAll.substring(0, splitAt).trim();
                String body = restAll.substring(splitAt).trim();
                if (StringUtils.isNotBlank(titleTail) && StringUtils.isNotBlank(body)
                    && titleTail.length() <= 40 && !looksLikeBodySentence(titleTail)) {
                    int dots = number.length() - number.replace(".", "").length();
                    return new HeadingCut(number + " " + titleTail, dots + 1, body);
                }
            }
        }
        return null;
    }

    private static int findTitleBodySplit(String restAll) {
        Matcher m = Pattern.compile("^(\\S{1,20})\\s+(\\S.+)$").matcher(restAll);
        if (m.matches()) {
            return m.start(2);
        }
        return -1;
    }

    private static boolean isStandaloneHeadingLine(String text) {
        String trimmed = StringUtils.trim(text);
        return trimmed.length() <= 100 && !looksLikeBodySentence(trimmed);
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
        if (CHAPTER_HEADING.matcher(normalized).matches() && normalized.length() <= 80) {
            return 1;
        }
        if (isSpecialSectionTitle(normalized)) {
            return 1;
        }
        if (CN_SECTION_HEADING.matcher(normalized).matches() && normalized.length() <= 80
            && !looksLikeBodySentence(normalized)) {
            return 1;
        }
        Matcher matcher = NUMBERED_HEADING.matcher(normalized);
        if (!matcher.matches()) {
            return 0;
        }
        String number = matcher.group(1).replace('．', '.');
        String titlePart = StringUtils.trim(matcher.group(2));
        if (titlePart.length() > 80) {
            return 0;
        }
        if (normalized.length() > 100) {
            return 0;
        }
        if (looksLikeBodySentence(normalized) || looksLikeBodySentence(titlePart)) {
            return 0;
        }
        int dots = number.length() - number.replace(".", "").length();
        return dots + 1;
    }

    /** 摘要/致谢/参考文献/总结等固定板块 */
    private static boolean isSpecialSectionTitle(String line) {
        String normalized = normalizeHeadingLine(line);
        if (StringUtils.isBlank(normalized) || normalized.length() > 40) {
            return false;
        }
        // 去掉末尾括号说明：参考文献（共20篇）
        String core = normalized.replaceAll("[（(].*$", "").trim();
        if (SPECIAL_SECTION.matcher(core).matches()) {
            return true;
        }
        return SPECIAL_SECTION.matcher(normalized).matches();
    }

    private static String normalizeHeadingLine(String line) {
        return StringUtils.trim(line)
            .replace('\u00A0', ' ')
            .replaceAll("[ \\t]+", " ");
    }
}
