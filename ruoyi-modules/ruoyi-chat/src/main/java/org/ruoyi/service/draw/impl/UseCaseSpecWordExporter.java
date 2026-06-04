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
import org.ruoyi.domain.dto.request.UseCaseSpecData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成用例说明 Word 表格文档
 */
final class UseCaseSpecWordExporter {

    private static final String FONT_BODY = "宋体";

    private static final Map<String, String> ROW_LABELS = new LinkedHashMap<>();

    static {
        ROW_LABELS.put("useCaseName", "用例名称");
        ROW_LABELS.put("role", "角色");
        ROW_LABELS.put("description", "用例说明");
        ROW_LABELS.put("preconditions", "前置条件");
        ROW_LABELS.put("postconditions", "后置条件");
        ROW_LABELS.put("basicFlow", "基本事件流");
        ROW_LABELS.put("extensionFlow", "扩展流程");
        ROW_LABELS.put("exceptionFlow", "异常事件流");
        ROW_LABELS.put("others", "其他");
    }

    private UseCaseSpecWordExporter() {
    }

    static byte[] export(UseCaseSpecData spec, int chapterNumber, int tableIndex) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            String caption = buildCaption(spec, chapterNumber, tableIndex);
            addCaption(doc, caption);
            writeSpecTable(doc, spec);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static String buildCaption(UseCaseSpecData spec, int chapter, int index) {
        String name = spec.getUseCaseName() != null ? spec.getUseCaseName().trim() : "用例";
        return "表" + chapter + "-" + index + " " + name + "用例说明";
    }

    private static void writeSpecTable(XWPFDocument doc, UseCaseSpecData spec) {
        XWPFTable table = doc.createTable(ROW_LABELS.size(), 2);
        table.setWidth("100%");
        int rowIdx = 0;
        int totalRows = ROW_LABELS.size();
        for (Map.Entry<String, String> entry : ROW_LABELS.entrySet()) {
            String value = getFieldValue(spec, entry.getKey());
            int rowNum = rowIdx + 1;
            XWPFTableRow row = table.getRow(rowIdx++);
            setCell(row.getCell(0), entry.getValue(), true, rowNum, totalRows);
            setCell(row.getCell(1), value, false, rowNum, totalRows);
        }
    }

    private static String getFieldValue(UseCaseSpecData spec, String key) {
        return switch (key) {
            case "useCaseName" -> nullToEmpty(spec.getUseCaseName());
            case "role" -> nullToEmpty(spec.getRole());
            case "description" -> nullToEmpty(spec.getDescription());
            case "preconditions" -> nullToEmpty(spec.getPreconditions());
            case "postconditions" -> nullToEmpty(spec.getPostconditions());
            case "basicFlow" -> nullToEmpty(spec.getBasicFlow());
            case "extensionFlow" -> nullToEmpty(spec.getExtensionFlow());
            case "exceptionFlow" -> nullToEmpty(spec.getExceptionFlow());
            case "others" -> {
                String o = spec.getOthers();
                yield (o == null || o.isBlank()) ? "无" : o.trim();
            }
            default -> "";
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static void setCell(XWPFTableCell cell, String text, boolean bold, int rowIndex, int totalRows) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(bold ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(10);
        applyCellBorders(cell, rowIndex, totalRows);
        if (bold) {
            cell.setColor("F5F5F5");
        }
    }

    private static void applyCellBorders(XWPFTableCell cell, int rowIndex, int totalRows) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        setBorder(borders.addNewTop(), STBorder.SINGLE, rowIndex == 1 ? 18 : 9);
        setBorder(borders.addNewBottom(), STBorder.SINGLE, rowIndex == totalRows ? 18 : 9);
        setBorder(borders.addNewLeft(), STBorder.SINGLE, 9);
        setBorder(borders.addNewRight(), STBorder.SINGLE, 9);
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
