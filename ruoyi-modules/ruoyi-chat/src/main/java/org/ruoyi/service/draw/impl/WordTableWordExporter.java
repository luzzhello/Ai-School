package org.ruoyi.service.draw.impl;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.ruoyi.domain.dto.request.WordTableBorderConfig;
import org.ruoyi.domain.dto.request.WordTableExportRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 可配置边框的 Word 表格导出
 */
final class WordTableWordExporter {

    private static final String FONT_BODY = "宋体";

    private WordTableWordExporter() {
    }

    static byte[] export(WordTableExportRequest request, List<String> headers, List<List<String>> dataRows) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            String title = request.getTitle() != null ? request.getTitle().trim() : "表格";
            addTitle(doc, title);
            writeTable(doc, headers, dataRows, request);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void writeTable(
        XWPFDocument doc,
        List<String> headers,
        List<List<String>> dataRows,
        WordTableExportRequest request
    ) {
        int cols = headers.size();
        int totalRows = 1 + dataRows.size();
        XWPFTable table = doc.createTable(totalRows, cols);
        table.setWidth("100%");

        WordTableBorderConfig headerBorder = orDefault(request.getHeaderBorder());
        WordTableBorderConfig dataBorder = orDefault(request.getDataRowBorder());
        WordTableBorderConfig lastBorder = orDefault(request.getLastRowBorder());

        XWPFTableRow headerRow = table.getRow(0);
        for (int c = 0; c < cols; c++) {
            setCell(headerRow.getCell(c), safe(headers.get(c)), true, headerBorder);
        }

        for (int r = 0; r < dataRows.size(); r++) {
            boolean isLast = r == dataRows.size() - 1;
            WordTableBorderConfig border = isLast ? mergeBorder(dataBorder, lastBorder) : dataBorder;
            List<String> row = dataRows.get(r);
            XWPFTableRow tableRow = table.getRow(r + 1);
            for (int c = 0; c < cols; c++) {
                String val = c < row.size() ? row.get(c) : "";
                setCell(tableRow.getCell(c), safe(val), false, border);
            }
        }
    }

    /** 最后一行：数据行边框与最后一行边框取较大值 */
    private static WordTableBorderConfig mergeBorder(WordTableBorderConfig data, WordTableBorderConfig last) {
        WordTableBorderConfig m = new WordTableBorderConfig();
        m.setTop(max(data.getTop(), last.getTop()));
        m.setBottom(max(data.getBottom(), last.getBottom()));
        m.setLeft(max(data.getLeft(), last.getLeft()));
        m.setRight(max(data.getRight(), last.getRight()));
        return m;
    }

    private static int max(Integer a, Integer b) {
        int x = a != null ? a : 0;
        int y = b != null ? b : 0;
        return Math.max(x, y);
    }

    private static WordTableBorderConfig orDefault(WordTableBorderConfig cfg) {
        return cfg != null ? cfg : new WordTableBorderConfig();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static void setCell(XWPFTableCell cell, String text, boolean bold, WordTableBorderConfig border) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(10);
        applyBorders(cell, border);
        if (bold) {
            cell.setColor("F5F5F5");
        }
    }

    private static void applyBorders(XWPFTableCell cell, WordTableBorderConfig cfg) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        applySide(borders.addNewTop(), cfg.getTop());
        applySide(borders.addNewBottom(), cfg.getBottom());
        applySide(borders.addNewLeft(), cfg.getLeft());
        applySide(borders.addNewRight(), cfg.getRight());
    }

    private static void applySide(CTBorder border, Integer size) {
        int sz = size != null ? size : 0;
        if (sz > 0) {
            border.setVal(STBorder.SINGLE);
            border.setSz(BigInteger.valueOf(sz));
            border.setColor("000000");
        } else {
            border.setVal(STBorder.NIL);
        }
    }

    private static void addTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(120);
        p.setSpacingAfter(100);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(12);
        run.setBold(true);
    }

    static List<String> normalizeHeaders(List<String> headers, int colCount) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < colCount; i++) {
            if (headers != null && i < headers.size() && headers.get(i) != null && !headers.get(i).isBlank()) {
                result.add(headers.get(i).trim());
            } else {
                result.add("列" + (i + 1));
            }
        }
        return result;
    }

    static List<List<String>> normalizeRows(List<List<String>> rows, int rowCount, int colCount) {
        List<List<String>> result = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) {
            List<String> line = new ArrayList<>();
            List<String> src = rows != null && r < rows.size() ? rows.get(r) : List.of();
            for (int c = 0; c < colCount; c++) {
                line.add(c < src.size() && src.get(c) != null ? src.get(c).trim() : "");
            }
            result.add(line);
        }
        return result;
    }
}
