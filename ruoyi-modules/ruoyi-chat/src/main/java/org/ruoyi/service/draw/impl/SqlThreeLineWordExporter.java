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
import org.ruoyi.domain.dto.request.SqlThreeLineExportRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 生成 SQL 表结构 Word 文档
 */
final class SqlThreeLineWordExporter {

    private static final String FONT_BODY = "宋体";
    private static final String FONT_HEADING = "黑体";

    private static final Map<String, String> COLUMN_LABELS = new LinkedHashMap<>();

    static {
        COLUMN_LABELS.put("index", "序号");
        COLUMN_LABELS.put("name", "字段名称");
        COLUMN_LABELS.put("type", "类型");
        COLUMN_LABELS.put("length", "长度");
        COLUMN_LABELS.put("primaryKey", "主键");
        COLUMN_LABELS.put("remark", "备注");
        COLUMN_LABELS.put("constraint", "约束");
    }

    private SqlThreeLineWordExporter() {
    }

    static byte[] export(List<SqlTableDocParser.SqlTableDef> tables, SqlThreeLineExportRequest options) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            boolean fullDocument = "fullDocument".equalsIgnoreCase(options.getDocMode());
            int chapter = options.getChapterNumber() != null ? options.getChapterNumber() : 4;
            if (fullDocument) {
                writeFullDocument(doc, tables, options, chapter);
            } else {
                boolean threeLine = "threeLine".equalsIgnoreCase(options.getTableStyle());
                for (int i = 0; i < tables.size(); i++) {
                    writeTableBlock(doc, tables.get(i), i + 1, options, threeLine, chapter);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void writeFullDocument(
        XWPFDocument doc,
        List<SqlTableDocParser.SqlTableDef> tables,
        SqlThreeLineExportRequest options,
        int chapter
    ) {
        addHeading(doc, chapter + ".3 数据库设计", 1);
        addHeading(doc, chapter + ".3.1 数据库详细设计", 2);
        addBody(doc, "数据项和数据结构如下：", true);
        for (int i = 0; i < tables.size(); i++) {
            addBody(doc, buildTableSummaryLine(tables.get(i), i + 1), true);
        }
        addHeading(doc, chapter + ".3.2 数据库概念设计", 2);
        addBody(doc, "您可以在这里放置ER图等内容。", true);
        addHeading(doc, chapter + ".3.3 数据库表设计", 2);
        addBody(doc, buildTablesOverview(tables), false);
        for (int i = 0; i < tables.size(); i++) {
            writeTableBlock(doc, tables.get(i), i + 1, options, true, chapter);
        }
    }

    private static void writeTableBlock(
        XWPFDocument doc,
        SqlTableDocParser.SqlTableDef table,
        int tableIndex,
        SqlThreeLineExportRequest options,
        boolean threeLine,
        int chapter
    ) {
        addCaption(doc, "表" + chapter + "-" + tableIndex + " " + table.displayTitle());
        List<String> keys = options.getColumns();
        XWPFTable xwpfTable = doc.createTable(1 + table.columns().size(), keys.size());
        xwpfTable.setWidth("100%");
        XWPFTableRow headerRow = xwpfTable.getRow(0);
        for (int c = 0; c < keys.size(); c++) {
            setCellText(headerRow.getCell(c), labelOf(keys.get(c)), true, threeLine, true, 0, table.columns().size());
        }
        List<Map<String, String>> rows = buildRows(table, options.getTypeCase());
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow dataRow = xwpfTable.getRow(r + 1);
            Map<String, String> row = rows.get(r);
            for (int c = 0; c < keys.size(); c++) {
                String val = row.getOrDefault(keys.get(c), "-");
                if (val == null || val.isBlank()) {
                    val = "-";
                }
                setCellText(dataRow.getCell(c), val, false, threeLine, false, r, table.columns().size());
            }
        }
        addEmptyLine(doc);
    }

    private static List<Map<String, String>> buildRows(SqlTableDocParser.SqlTableDef table, String typeCase) {
        boolean upper = !"lower".equalsIgnoreCase(typeCase);
        List<Map<String, String>> rows = new java.util.ArrayList<>();
        int idx = 1;
        for (SqlTableDocParser.SqlColumnRow col : table.columns()) {
            String type = upper ? col.type().toUpperCase(Locale.ROOT) : col.type().toLowerCase(Locale.ROOT);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("index", String.valueOf(idx++));
            row.put("name", col.name());
            row.put("type", type);
            row.put("length", col.length());
            row.put("primaryKey", col.primaryKey() ? "是" : "否");
            row.put("remark", col.remark());
            row.put("constraint", col.constraint());
            rows.add(row);
        }
        return rows;
    }

    private static String labelOf(String key) {
        return COLUMN_LABELS.getOrDefault(key, key);
    }

    private static void setCellText(
        XWPFTableCell cell,
        String text,
        boolean bold,
        boolean threeLine,
        boolean isHeader,
        int dataRowIndex,
        int totalDataRows
    ) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(10);
        applyBorders(cell, threeLine, isHeader, dataRowIndex, totalDataRows);
        if (!threeLine && isHeader) {
            cell.setColor("E8E8E8");
        }
    }

    private static void applyBorders(
        XWPFTableCell cell,
        boolean threeLine,
        boolean isHeader,
        int dataRowIndex,
        int totalDataRows
    ) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        if (!threeLine) {
            setBorder(borders.addNewTop(), STBorder.SINGLE, 9);
            setBorder(borders.addNewBottom(), STBorder.SINGLE, 9);
            setBorder(borders.addNewLeft(), STBorder.SINGLE, 9);
            setBorder(borders.addNewRight(), STBorder.SINGLE, 9);
            return;
        }
        setBorder(borders.addNewLeft(), STBorder.NIL, 0);
        setBorder(borders.addNewRight(), STBorder.NIL, 0);
        if (isHeader) {
            setBorder(borders.addNewTop(), STBorder.SINGLE, 18);
            setBorder(borders.addNewBottom(), STBorder.NIL, 0);
        } else {
            boolean first = dataRowIndex == 0;
            boolean last = dataRowIndex == totalDataRows - 1;
            setBorder(borders.addNewTop(), first ? STBorder.SINGLE : STBorder.NIL, first ? 9 : 0);
            setBorder(borders.addNewBottom(), last ? STBorder.SINGLE : STBorder.NIL, last ? 18 : 0);
        }
    }

    private static void setBorder(CTBorder border, STBorder.Enum style, int size) {
        border.setVal(style);
        if (size > 0) {
            border.setSz(BigInteger.valueOf(size));
            border.setColor("000000");
        }
    }

    private static void addCaption(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(200);
        p.setSpacingAfter(80);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(10);
    }

    private static void addHeading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontFamily(FONT_HEADING);
        run.setFontSize(level == 1 ? 14 : 12);
    }

    private static void addBody(XWPFDocument doc, String text, boolean indent) {
        XWPFParagraph p = doc.createParagraph();
        if (indent) {
            p.setIndentationFirstLine(480);
        }
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(12);
    }

    private static void addEmptyLine(XWPFDocument doc) {
        doc.createParagraph();
    }

    private static String buildTableSummaryLine(SqlTableDocParser.SqlTableDef table, int index) {
        String fields = table.columns().stream()
            .map(col -> {
                String r = col.remark();
                return (r != null && !r.isBlank()) ? r : col.name();
            })
            .filter(s -> s != null && !s.isBlank())
            .reduce((a, b) -> a + "、" + b)
            .orElse("");
        return index + ". " + table.displayTitle() + ": " + fields + "。";
    }

    private static String buildTablesOverview(List<SqlTableDocParser.SqlTableDef> tables) {
        String names = tables.stream()
            .map(t -> extractChineseTableName(t.displayTitle()))
            .reduce((a, b) -> a + "、" + b)
            .orElse("");
        return "数据库总共涉及" + tables.size() + "张表，分别是" + names + "。";
    }

    private static String extractChineseTableName(String displayTitle) {
        int idx = displayTitle.indexOf('(');
        if (idx > 0) {
            return displayTitle.substring(0, idx).trim();
        }
        return displayTitle;
    }
}
