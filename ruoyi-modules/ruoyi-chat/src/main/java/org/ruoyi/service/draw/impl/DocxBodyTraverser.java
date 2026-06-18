package org.ruoyi.service.draw.impl;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.ruoyi.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 按文档顺序遍历正文：段落 + 表格行（一行各单元格合并为一条）
 */
final class DocxBodyTraverser {

    private DocxBodyTraverser() {
    }

    static void traverse(XWPFDocument document, BiConsumer<XWPFParagraph, String> onParagraph, Consumer<String> onTableRow) {
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                if (!DocumentReducedPatcher.isEditableParagraph(paragraph)) {
                    continue;
                }
                String text = StringUtils.trim(paragraph.getText());
                if (StringUtils.isNotBlank(text)) {
                    onParagraph.accept(paragraph, text);
                }
            }
            else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    String rowText = mergeTableRow(row);
                    if (StringUtils.isNotBlank(rowText)) {
                        onTableRow.accept(rowText);
                    }
                }
            }
        }
    }

    static List<String> extractBlocksInOrder(XWPFDocument document) {
        List<String> blocks = new ArrayList<>();
        traverse(document,
            (paragraph, text) -> blocks.add(text),
            blocks::add);
        return blocks;
    }

    static String mergeTableRow(XWPFTableRow row) {
        List<String> cells = new ArrayList<>();
        for (XWPFTableCell cell : row.getTableCells()) {
            String cellText = normalizeCellText(cell.getText());
            if (StringUtils.isNotBlank(cellText)) {
                cells.add(cellText);
            }
        }
        return String.join("\t", cells);
    }

    private static String normalizeCellText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
    }
}
