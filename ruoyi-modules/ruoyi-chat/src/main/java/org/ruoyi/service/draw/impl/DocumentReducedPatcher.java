package org.ruoyi.service.draw.impl;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSettings;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSettings;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 在原 DOCX 中仅替换正文文字，保留目录域、书签、图片、公式等版式结构
 */
final class DocumentReducedPatcher {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？!?；;])");
    private static final Pattern TOC_STYLE = Pattern.compile("(?i)(toc\\s*\\d+|目录\\s*\\d+)");

    private DocumentReducedPatcher() {
    }

    static String extractEditableText(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<String> blocks = DocxBodyTraverser.extractBlocksInOrder(document);
            if (blocks.isEmpty()) {
                return "";
            }
            return String.join("\n\n", blocks);
        }
    }

    static boolean isEditableParagraph(XWPFParagraph paragraph) {
        return isEditableTextParagraph(paragraph);
    }

    static byte[] patchDocx(InputStream inputStream, List<String> reducedSegments, String splitMode) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<XWPFParagraph> paragraphs = collectEditableParagraphs(document);
            if (paragraphs.isEmpty()) {
                throw new ServiceException("文档中没有可替换的正文段落");
            }
            applySegmentsToDocx(paragraphs, reducedSegments, splitMode);
            enableUpdateFieldsOnOpen(document);
            document.write(out);
            return out.toByteArray();
        }
    }

    private static List<XWPFParagraph> collectEditableParagraphs(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = new ArrayList<>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (isEditableTextParagraph(paragraph)) {
                paragraphs.add(paragraph);
            }
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        if (isEditableTextParagraph(paragraph)) {
                            paragraphs.add(paragraph);
                        }
                    }
                }
            }
        }
        return paragraphs;
    }

    private static boolean isEditableTextParagraph(XWPFParagraph paragraph) {
        if (isProtectedParagraph(paragraph)) {
            return false;
        }
        if (paragraph.getCTP().sizeOfOMathArray() > 0 || paragraph.getCTP().sizeOfOMathParaArray() > 0) {
            return false;
        }
        if (StringUtils.isBlank(paragraph.getText())) {
            return false;
        }
        for (XWPFRun run : paragraph.getRuns()) {
            if (hasEditableText(run)) {
                return true;
            }
        }
        return false;
    }

    /** 目录域、目录样式段落、交叉引用等不参与降重 */
    private static boolean isProtectedParagraph(XWPFParagraph paragraph) {
        if (isTocStyleParagraph(paragraph)) {
            return true;
        }
        return containsStructuralField(paragraph);
    }

    private static boolean isTocStyleParagraph(XWPFParagraph paragraph) {
        String styleId = StringUtils.defaultString(paragraph.getStyleID());
        String style = StringUtils.defaultString(paragraph.getStyle());
        return TOC_STYLE.matcher(styleId).find() || TOC_STYLE.matcher(style).find();
    }

    private static boolean containsStructuralField(XWPFParagraph paragraph) {
        if (paragraph.getCTP().sizeOfFldSimpleArray() > 0) {
            return true;
        }
        for (CTR ctr : paragraph.getCTP().getRList()) {
            if (ctr.sizeOfFldCharArray() > 0) {
                return true;
            }
            for (CTText instr : ctr.getInstrTextArray()) {
                String instruction = StringUtils.defaultString(instr.getStringValue()).toUpperCase(Locale.ROOT);
                if (instruction.contains(" TOC")
                    || instruction.startsWith("TOC ")
                    || instruction.contains("PAGEREF")
                    || instruction.contains("HYPERLINK \\L")
                    || instruction.contains(" REF ")
                    || instruction.contains("SEQ ")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasEditableText(XWPFRun run) {
        if (run.getEmbeddedPictures() != null && !run.getEmbeddedPictures().isEmpty()) {
            return false;
        }
        if (hasStructuralMarkup(run)) {
            return false;
        }
        return StringUtils.isNotBlank(run.text());
    }

    private static boolean hasStructuralMarkup(XWPFRun run) {
        CTR ctr = run.getCTR();
        return ctr.sizeOfFldCharArray() > 0 || ctr.sizeOfInstrTextArray() > 0;
    }

    private static void applySegmentsToDocx(List<XWPFParagraph> paragraphs, List<String> segments, String splitMode) {
        if ("sentence".equals(splitMode)) {
            int segmentIndex = 0;
            for (XWPFParagraph paragraph : paragraphs) {
                if (segmentIndex >= segments.size()) {
                    break;
                }
                int sentenceCount = countSentences(paragraph.getText());
                String chunk = String.join("", segments.subList(segmentIndex, Math.min(segmentIndex + sentenceCount, segments.size())));
                replaceDocxParagraphText(paragraph, chunk);
                segmentIndex += sentenceCount;
            }
            return;
        }
        for (int i = 0; i < paragraphs.size() && i < segments.size(); i++) {
            replaceDocxParagraphText(paragraphs.get(i), segments.get(i));
        }
    }

    /**
     * 只改纯文字 Run，保留书签、域代码、图片等结构
     */
    private static void replaceDocxParagraphText(XWPFParagraph paragraph, String newText) {
        List<XWPFRun> runs = paragraph.getRuns();
        int targetRun = -1;
        for (int i = 0; i < runs.size(); i++) {
            if (hasEditableText(runs.get(i))) {
                targetRun = i;
                break;
            }
        }
        if (targetRun < 0) {
            return;
        }
        runs.get(targetRun).setText(newText, 0);
        for (int i = 0; i < runs.size(); i++) {
            if (i == targetRun) {
                continue;
            }
            XWPFRun run = runs.get(i);
            if (!hasEditableText(run) || hasStructuralMarkup(run)) {
                continue;
            }
            run.setText("", 0);
        }
    }

    /** 打开文档时让 Word 自动刷新目录、页码等域 */
    private static void enableUpdateFieldsOnOpen(XWPFDocument document) {
        XWPFSettings settings = document.getSettings();
        if (settings == null) {
            return;
        }
        CTSettings ctSettings = settings.getCTSettings();
        if (ctSettings.isSetUpdateFields()) {
            ctSettings.getUpdateFields().setVal(true);
        }
        else {
            ctSettings.addNewUpdateFields().setVal(true);
        }
    }

    private static int countSentences(String text) {
        if (StringUtils.isBlank(text)) {
            return 1;
        }
        String[] parts = SENTENCE_SPLIT.split(text.trim());
        int count = 0;
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                count++;
            }
        }
        return count > 0 ? count : 1;
    }
}
