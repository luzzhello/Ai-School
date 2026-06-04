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
import org.ruoyi.domain.dto.request.FuncTestCaseData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * 生成功能测试用例 Word 表格
 */
final class FuncTestWordExporter {

    private static final String FONT_BODY = "宋体";
    private static final String[] HEADERS = {
        "用例编号", "用例名称", "前置条件", "测试步骤", "预期结果", "测试结果"
    };

    private FuncTestWordExporter() {
    }

    static byte[] export(String documentTitle, List<FuncTestCaseData> cases, int chapter, int tableIndex) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            String title = documentTitle != null ? documentTitle.trim() : "功能测试";
            addCaption(doc, "表" + chapter + "-" + tableIndex + " " + title + "功能测试");
            writeTable(doc, cases);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void writeTable(XWPFDocument doc, List<FuncTestCaseData> cases) {
        int rows = 1 + cases.size();
        XWPFTable table = doc.createTable(rows, HEADERS.length);
        table.setWidth("100%");
        XWPFTableRow headerRow = table.getRow(0);
        for (int c = 0; c < HEADERS.length; c++) {
            setCell(headerRow.getCell(c), HEADERS[c], true, 1, rows);
        }
        for (int r = 0; r < cases.size(); r++) {
            FuncTestCaseData item = cases.get(r);
            XWPFTableRow row = table.getRow(r + 1);
            setCell(row.getCell(0), safe(item.getCaseId()), false, r + 2, rows);
            setCell(row.getCell(1), safe(item.getCaseName()), false, r + 2, rows);
            setCell(row.getCell(2), safe(item.getPreconditions()), false, r + 2, rows);
            setCell(row.getCell(3), safe(item.getTestSteps()), false, r + 2, rows);
            setCell(row.getCell(4), safe(item.getExpectedResult()), false, r + 2, rows);
            setCell(row.getCell(5), safe(item.getTestResult()), false, r + 2, rows);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static void setCell(XWPFTableCell cell, String text, boolean bold, int rowNum, int totalRows) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(bold ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(9);
        applyBorders(cell, rowNum, totalRows);
        if (bold) {
            cell.setColor("E8E8E8");
        }
    }

    private static void applyBorders(XWPFTableCell cell, int rowNum, int totalRows) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        setBorder(borders.addNewTop(), STBorder.SINGLE, rowNum == 1 ? 12 : 6);
        setBorder(borders.addNewBottom(), STBorder.SINGLE, rowNum == totalRows ? 12 : 6);
        setBorder(borders.addNewLeft(), STBorder.SINGLE, 6);
        setBorder(borders.addNewRight(), STBorder.SINGLE, 6);
    }

    private static void setBorder(CTBorder border, STBorder.Enum style, int size) {
        border.setVal(style);
        border.setSz(BigInteger.valueOf(size));
        border.setColor("000000");
    }

    private static void addCaption(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(120);
        p.setSpacingAfter(100);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(10);
    }
}
