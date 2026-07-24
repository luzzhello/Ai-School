package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHdrFtrRef;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVerticalJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabTlc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperTemplateStyleMapping;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.domain.paper.TocNode;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Base64;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 论文生成智能体——Word(.docx) 导出服务。
 * <p>
 * 读取会话目录与已生成内容，按会话有效排版配置（默认等同大连海洋大学版式）组装 docx：
 * A4；页边距/正文字体字号/行距/标题样式来自 {@link PaperFormatConfig}；
 * 不加固定页眉文案（学校名/题目由用户模板自行决定）；
 * 页码从目录起编（章前罗马、第一章起阿拉伯），页脚居中；三线表。
 * 对应 PRD「3.6 文档排版与导出模块」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordExportService {

    /** 分节页码策略：无页码 / 罗马（目录） / 阿拉伯（正文） */
    private enum PageNumberMode {
        NONE,
        ROMAN,
        ARABIC
    }

    /** 与 {@link PaperFormatDefaults#dalianOcean()} 一致的兜底常量（仅当 effective 字段为空时使用） */
    private static final String FONT_BODY = "宋体";
    private static final String FONT_HEADING = "黑体";
    private static final String FONT_CODE = "Consolas";
    /** 英文 / 数字 / 表内西文 */
    private static final String FONT_TABLE = "Times New Roman";
    private static final double FONT_SIZE_BODY = 10.5;
    private static final double FONT_SIZE_TITLE = 18;
    private static final double FONT_SIZE_H1 = 16;
    private static final double FONT_SIZE_H2 = 12;
    private static final double FONT_SIZE_H3 = 10.5;
    private static final double FONT_SIZE_CAPTION = 9;
    private static final double FONT_SIZE_FOOTER = 9;
    private static final double FONT_SIZE_TABLE = 10.5;
    private static final double BODY_LINE_SPACING_PT = 18;
    private static final double HEADING_SPACING_PT = 12;
    private static final int BODY_FIRST_LINE_INDENT_CHARS = 2;
    private static final double MARGIN_LEFT_MM = 30;
    private static final double MARGIN_RIGHT_MM = 25;
    private static final double MARGIN_TOP_MM = 30;
    private static final double MARGIN_BOTTOM_MM = 25;

    /** A4 尺寸（twips）：210mm × 297mm */
    private static final int A4_WIDTH = 11906;
    private static final int A4_HEIGHT = 16838;
    /** 页眉边距 23mm、页脚边距 18mm（一期不进 format_json） */
    private static final int HEADER_DISTANCE = 1304;
    private static final int FOOTER_DISTANCE = 1021;
    /** 正文区可用高度近似（pt），含页眉页脚留白 */
    private static final int CONTENT_MAX_HEIGHT_PT = 620;
    /** 单元格左右内边距（twips），避免文字贴边 */
    private static final int TABLE_CELL_PAD_TWIPS = 40;
    /**
     * 数据库表结构六列固定比例（%）：字段名称 / 类型 / 长度 / 允许空值 / 主键 / 备注。
     * 短列给足宽度，避免「长度」「主键」被挤成一字一换行。
     */
    private static final int[] DB_TABLE_COL_PCT = {20, 14, 10, 14, 10, 32};
    /** 三线表：上下线 1.5 磅、中间线 0.5 磅（OOXML sz 单位为 1/8 磅） */
    private static final int THREE_LINE_OUTER = 12;
    private static final int THREE_LINE_INNER = 4;
    private static final Pattern SVG_VIEWBOX = Pattern.compile(
        "viewBox\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_WIDTH = Pattern.compile(
        "\\bwidth\\s*=\\s*[\"']([\\d.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_HEIGHT = Pattern.compile(
        "\\bheight\\s*=\\s*[\"']([\\d.]+)", Pattern.CASE_INSENSITIVE);

    private static final String STYLE_HEADING1 = "Heading1";
    private static final String STYLE_HEADING2 = "Heading2";
    private static final String STYLE_HEADING3 = "Heading3";
    private static final String STYLE_SECTION_TITLE = "SectionTitle";

    /** 参考文献角标 [1] / [1,2] / [1-3] */
    private static final Pattern CITATION = Pattern.compile("\\[\\d+(?:[,，\\-]\\d+)*\\]");
    /** 图标题：图1-1 / 图4.1 */
    private static final Pattern FIGURE_CAPTION = Pattern.compile("^图\\s*\\d+[.\\-－]?\\d*.*");
    /** 表标题：表1-1 / 表4.1 */
    private static final Pattern TABLE_CAPTION = Pattern.compile("^表\\s*\\d+[.\\-－]?\\d*.*");
    /** Markdown 图片：![图3-1 说明](data:image/png;base64,...) 或 URL */
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("^!\\[(.*?)]\\((.+?)\\)\\s*$");
    /** 插图在线编辑元数据（正文内隐藏，导出 Word 须剥离） */
    private static final Pattern PAPER_DRAW_INLINE = Pattern.compile(
        "\\[\\[\\[PAPER_DRAW:\\{.*?\\}]]]|<<<PAPER_DRAW:\\{.*?\\}>>>|<!--\\s*paper-draw\\s+\\{.*?\\}\\s*-->",
        Pattern.DOTALL);
    /** 摘要中英文分界 */
    private static final Pattern ABSTRACT_HEADER = Pattern.compile("(?im)^Abstract:?\\s*$");
    private static final Pattern ABSTRACT_LINE = Pattern.compile("(?im)^ABSTRACT\\s*$");
    private static final Pattern CHINESE_KEYWORDS = Pattern.compile("(?m)^关键词[：:]");
    private static final Pattern ENGLISH_KEYWORDS = Pattern.compile("(?im)^Keywords\\s*:");

    private final PaperSessionStore paperSessionStore;
    private final PaperAssetService paperAssetService;
    private final PaperTemplateService paperTemplateService;
    private final PaperFormatTemplateService paperFormatTemplateService;
    private final PaperSessionCustomFormatService paperSessionCustomFormatService;
    private final PaperExportLayoutEstimator layoutEstimator = new PaperExportLayoutEstimator();

    /** 单次导出线程内使用的模板样式映射 */
    private final ThreadLocal<PaperTemplateStyleMapping> exportTemplateStyles = new ThreadLocal<>();
    /** 单次导出线程内有效排版配置 */
    private final ThreadLocal<PaperFormatConfig> exportFormat = new ThreadLocal<>();
    /** 正文段落样式上下文：致谢章使用独立字体字号 */
    private final ThreadLocal<Boolean> acknowledgmentBody = new ThreadLocal<>();

    /**
     * 导出论文为 docx 字节数组。
     *
     * @param sessionId 会话 id
     * @return docx 文件字节
     */
    public byte[] export(String sessionId) {
        PaperSession session = paperSessionStore.get(sessionId);
        if (session == null) {
            throw new ServiceException("会话不存在或已过期");
        }
        PaperFormatConfig effective = paperFormatTemplateService.resolveEffective(session);
        exportFormat.set(effective);
        try (InputStream templateIn = openExportTemplate(session);
             XWPFDocument doc = openCleanTemplateDocument(templateIn)) {
            PaperTemplateStyleMapping templateStyles = paperTemplateService.getStyleMapping();
            exportTemplateStyles.set(templateStyles);
            applyPageSetup(doc);
            applyHeaderAndFooter(doc);
            patchTemplateStyles(doc);
            writeTitle(doc, session.getTitle(), templateStyles);
            writeBody(doc, session);
            ensureBodySectionPageSetup(doc);
            applyDocumentSettings(doc);
            disableProofingOnAllRuns(doc);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("导出论文 Word 失败, sessionId={}", sessionId, e);
            throw new ServiceException("导出 Word 失败");
        } finally {
            exportTemplateStyles.remove();
            exportFormat.remove();
            acknowledgmentBody.remove();
        }
    }

    private InputStream openExportTemplate(PaperSession session) {
        if (PaperSessionCustomFormatService.isCustomMode(session)) {
            return paperSessionCustomFormatService.openCustomDocx(session);
        }
        try {
            return paperFormatTemplateService.openDocx(session.getFormatTemplateId());
        } catch (Exception e) {
            log.warn("打开排版模板 docx 失败，回退全局模板: {}", e.getMessage());
            return paperTemplateService.openTemplateInputStream();
        }
    }

    private PaperTemplateStyleMapping currentTemplateStyles() {
        PaperTemplateStyleMapping styles = exportTemplateStyles.get();
        return styles != null ? styles : PaperTemplateStyleMapping.defaults();
    }

    /** 当前导出有效配置；未设置时回退大连海洋默认 */
    private PaperFormatConfig fmt() {
        PaperFormatConfig config = exportFormat.get();
        return config != null ? config : PaperFormatDefaults.dalianOcean();
    }

    private String fontBody() {
        return firstNonBlank(fmt().getFont().getBodyEastAsia(), FONT_BODY);
    }

    private String fontBodyAscii() {
        return firstNonBlank(fmt().getFont().getBodyAscii(), FONT_TABLE);
    }

    private String fontHeading() {
        return firstNonBlank(fmt().getFont().getHeadingEastAsia(), FONT_HEADING);
    }

    /** 按标题级别取东文字体，未配置则回退统一标题字体 */
    private String fontHeadingAt(int level) {
        var font = fmt().getFont();
        String specific = switch (Math.max(1, Math.min(level, 5))) {
            case 1 -> font.getHeading1EastAsia();
            case 2 -> font.getHeading2EastAsia();
            case 3 -> font.getHeading3EastAsia();
            case 4 -> font.getHeading4EastAsia();
            case 5 -> font.getHeading5EastAsia();
            default -> null;
        };
        return firstNonBlank(specific, fontHeading());
    }

    private String fontHeadingAscii() {
        return firstNonBlank(fmt().getFont().getHeadingAscii(), FONT_TABLE);
    }

    private String fontTableEastAsia() {
        return firstNonBlank(fmt().getFont().getTableEastAsia(), FONT_BODY);
    }

    private String fontTableAscii() {
        return firstNonBlank(fmt().getFont().getTableAscii(), FONT_TABLE);
    }

    private String fontCode() {
        return firstNonBlank(fmt().getFont().getCode(), FONT_CODE);
    }

    private String fontFooter() {
        return firstNonBlank(fmt().getFont().getFooterEastAsia(), FONT_BODY);
    }

    private String fontAbstract() {
        return firstNonBlank(fmt().getFont().getAbstractEastAsia(), fontBody());
    }

    private String fontKeyword() {
        return firstNonBlank(fmt().getFont().getKeywordEastAsia(), fontBody());
    }

    private String fontReference() {
        return firstNonBlank(fmt().getFont().getReferenceEastAsia(), fontBody());
    }

    private String fontAcknowledgment() {
        return firstNonBlank(fmt().getFont().getAcknowledgmentEastAsia(), fontBody());
    }

    private double fontSizeBody() {
        return firstNonNull(fmt().getFontSize().getBody(), FONT_SIZE_BODY);
    }

    private double fontSizeTitle() {
        return firstNonNull(fmt().getFontSize().getTitle(), FONT_SIZE_TITLE);
    }

    private double fontSizeH1() {
        return firstNonNull(fmt().getFontSize().getHeading1(), FONT_SIZE_H1);
    }

    private double fontSizeH2() {
        return firstNonNull(fmt().getFontSize().getHeading2(), FONT_SIZE_H2);
    }

    private double fontSizeH3() {
        return firstNonNull(fmt().getFontSize().getHeading3(), FONT_SIZE_H3);
    }

    private double fontSizeH4() {
        return firstNonNull(fmt().getFontSize().getHeading4(), fontSizeH3());
    }

    private double fontSizeH5() {
        return firstNonNull(fmt().getFontSize().getHeading5(), fontSizeH3());
    }

    private double fontSizeHeadingAt(int level) {
        return switch (Math.max(1, Math.min(level, 5))) {
            case 1 -> fontSizeH1();
            case 2 -> fontSizeH2();
            case 3 -> fontSizeH3();
            case 4 -> fontSizeH4();
            case 5 -> fontSizeH5();
            default -> fontSizeH3();
        };
    }

    private double fontSizeCaption() {
        return firstNonNull(fmt().getFontSize().getCaption(), FONT_SIZE_CAPTION);
    }

    private double fontSizeFooter() {
        return firstNonNull(fmt().getFontSize().getFooter(), FONT_SIZE_FOOTER);
    }

    private double fontSizeTable() {
        return firstNonNull(fmt().getFontSize().getBody(), FONT_SIZE_TABLE);
    }

    private double fontSizeReference() {
        return firstNonNull(fmt().getFontSize().getReference(), FONT_SIZE_BODY);
    }

    private double fontSizeAbstractLabel() {
        return firstNonNull(fmt().getFontSize().getAbstractLabel(), FONT_SIZE_BODY);
    }

    private double fontSizeAbstractBody() {
        return firstNonNull(fmt().getFontSize().getAbstractBody(), fontSizeBody());
    }

    private double fontSizeKeyword() {
        return firstNonNull(fmt().getFontSize().getKeyword(), fontSizeBody());
    }

    private double fontSizeAcknowledgment() {
        return firstNonNull(fmt().getFontSize().getAcknowledgment(), fontSizeBody());
    }

    /** 正文区页脚格式：numeric / roman / dash / none */
    private String footerFormat() {
        PaperFormatConfig.HeaderFooter hf = fmt().nestedHeaderFooter();
        if (hf == null || StringUtils.isBlank(hf.getFooterFormat())) {
            return "numeric";
        }
        return hf.getFooterFormat().trim().toLowerCase();
    }

    private PageNumberMode resolveBodyPageNumberMode() {
        return switch (footerFormat()) {
            case "none" -> PageNumberMode.NONE;
            case "roman" -> PageNumberMode.ROMAN;
            default -> PageNumberMode.ARABIC;
        };
    }

    private boolean footerUsesDash() {
        return "dash".equals(footerFormat());
    }

    private boolean footerEnabled() {
        return !"none".equals(footerFormat());
    }

    private int marginLeftTwips() {
        return mmToTwips(firstNonNull(fmt().getPage().getMarginLeftMm(), MARGIN_LEFT_MM));
    }

    private int marginRightTwips() {
        return mmToTwips(firstNonNull(fmt().getPage().getMarginRightMm(), MARGIN_RIGHT_MM));
    }

    private int marginTopTwips() {
        return mmToTwips(firstNonNull(fmt().getPage().getMarginTopMm(), MARGIN_TOP_MM));
    }

    private int marginBottomTwips() {
        return mmToTwips(firstNonNull(fmt().getPage().getMarginBottomMm(), MARGIN_BOTTOM_MM));
    }

    private int contentWidthTwips() {
        return A4_WIDTH - marginLeftTwips() - marginRightTwips();
    }

    private int tocTabPos() {
        return contentWidthTwips();
    }

    private int tableWidthTwips() {
        return contentWidthTwips();
    }

    /** 正文区可用宽度（pt）= 纸宽 − 左右边距 */
    private int contentMaxWidthPt() {
        double left = firstNonNull(fmt().getPage().getMarginLeftMm(), MARGIN_LEFT_MM);
        double right = firstNonNull(fmt().getPage().getMarginRightMm(), MARGIN_RIGHT_MM);
        return (int) Math.round((210.0 - left - right) * 72.0 / 25.4);
    }

    private int bodyFirstLineCharsHundredths() {
        int chars = firstNonNull(fmt().getParagraph().getFirstLineIndentChars(), BODY_FIRST_LINE_INDENT_CHARS);
        return chars * 100;
    }

    private int bodyFirstLineTwips() {
        int chars = firstNonNull(fmt().getParagraph().getFirstLineIndentChars(), BODY_FIRST_LINE_INDENT_CHARS);
        return (int) Math.round(chars * fontSizeBody() * 20.0);
    }

    private static int mmToTwips(double mm) {
        // 与历史常量一致：1mm≈56.7 twips（30→1701，25→1418）
        return (int) Math.round(mm * 56.7);
    }

    private static int ptToTwips(double pt) {
        return (int) Math.round(pt * 20.0);
    }

    private static String firstNonBlank(String value, String fallback) {
        return StringUtils.isNotBlank(value) ? value : fallback;
    }

    private static <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static ParagraphAlignment resolveAlign(String align, ParagraphAlignment fallback) {
        if (align == null) {
            return fallback;
        }
        return switch (align.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> ParagraphAlignment.LEFT;
            case "center" -> ParagraphAlignment.CENTER;
            case "right" -> ParagraphAlignment.RIGHT;
            case "both", "justify" -> ParagraphAlignment.BOTH;
            default -> fallback;
        };
    }

    /**
     * 从模板打开「空白正文」文档：仅保留 styles/numbering/settings 与节属性（页边距等），
     * 彻底移除模板占位段落、自动目录 SDT 及页眉页脚。
     * <p>模板含数百段占位正文 + {@code w:sdt} 目录域；仅调用 {@link XWPFDocument#removeBodyElement(int)}
     * 会导致 POI 内存列表与 {@link CTBody} 不同步，写出时仍带模板目录，故需替换 body 并 round-trip 重载。
     */
    private XWPFDocument openCleanTemplateDocument(InputStream templateIn) throws IOException {
        try (XWPFDocument raw = new XWPFDocument(templateIn)) {
            resetHeaderFooters(raw);
            replaceBodyKeepSectionOnly(raw);
            applyDocumentSettings(raw);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            raw.write(buffer);
            return new XWPFDocument(new ByteArrayInputStream(buffer.toByteArray()));
        }
    }

    /** 新建空 body，仅复制原 sectPr（页边距、纸张、分节等） */
    private void replaceBodyKeepSectionOnly(XWPFDocument doc) {
        CTBody oldBody = doc.getDocument().getBody();
        CTSectPr sectPr = oldBody.isSetSectPr() ? (CTSectPr) oldBody.getSectPr().copy() : null;
        CTBody freshBody = CTBody.Factory.newInstance();
        if (sectPr != null) {
            freshBody.setSectPr(sectPr);
        }
        doc.getDocument().setBody(freshBody);
    }

    /**
     * 文档级设置：禁止自动更新域；隐藏拼写/语法波浪线（Vue、MyBatis 等专有名词不再标红）。
     * <p>poi-ooxml-lite 不含 STProof，故仅用 hideSpellingErrors / hideGrammaticalErrors（空元素即 true）。
     */
    private void applyDocumentSettings(XWPFDocument doc) {
        if (doc.getSettings() == null) {
            return;
        }
        var settings = doc.getSettings().getCTSettings();
        if (settings.isSetUpdateFields()) {
            settings.getUpdateFields().setVal(false);
        } else {
            settings.addNewUpdateFields().setVal(false);
        }
        // 「仅在此文档中隐藏拼写/语法错误」——先清再加，覆盖模板中的 false
        if (settings.isSetHideSpellingErrors()) {
            settings.unsetHideSpellingErrors();
        }
        settings.addNewHideSpellingErrors();
        if (settings.isSetHideGrammaticalErrors()) {
            settings.unsetHideGrammaticalErrors();
        }
        settings.addNewHideGrammaticalErrors();
        // 奇偶页眉不同时开启 evenAndOddHeaders
        PaperFormatConfig.HeaderFooter hf = fmt().nestedHeaderFooter();
        String odd = hf == null ? null : StringUtils.trim(hf.getOddHeader());
        String even = hf == null ? null : StringUtils.trim(hf.getEvenHeader());
        boolean needEvenOdd = StringUtils.isNotBlank(odd) && StringUtils.isNotBlank(even)
            && !odd.equals(even);
        if (settings.isSetEvenAndOddHeaders()) {
            settings.unsetEvenAndOddHeaders();
        }
        if (needEvenOdd) {
            settings.addNewEvenAndOddHeaders();
        }
    }

    /**
     * 为正文/表格/页眉页脚全部 run 写入 w:noProof，避免 Word/WPS 对 Vue、RESTful 等标红波浪线。
     */
    private void disableProofingOnAllRuns(XWPFDocument doc) {
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            disableProofingOnParagraph(paragraph);
        }
        for (XWPFTable table : doc.getTables()) {
            disableProofingOnTable(table);
        }
        for (XWPFHeader header : doc.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                disableProofingOnParagraph(paragraph);
            }
            for (XWPFTable table : header.getTables()) {
                disableProofingOnTable(table);
            }
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                disableProofingOnParagraph(paragraph);
            }
            for (XWPFTable table : footer.getTables()) {
                disableProofingOnTable(table);
            }
        }
    }

    private void disableProofingOnTable(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    disableProofingOnParagraph(paragraph);
                }
                for (XWPFTable nested : cell.getTables()) {
                    disableProofingOnTable(nested);
                }
            }
        }
    }

    private void disableProofingOnParagraph(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
            if (rPr.sizeOfNoProofArray() == 0) {
                rPr.addNewNoProof();
            }
        }
    }

    private void resetHeaderFooters(XWPFDocument doc) {
        for (XWPFHeader header : doc.getHeaderList()) {
            header.setHeaderFooter(CTHdrFtr.Factory.newInstance());
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            footer.setHeaderFooter(CTHdrFtr.Factory.newInstance());
        }
    }

    /** 页边距 + 页眉/页脚距离；文档末节默认正文阿拉伯页码从 1 起 */
    private void applyPageSetup(XWPFDocument doc) {
        Boolean enabled = fmt().getExport().getApplyPageSetup();
        if (enabled != null && !enabled) {
            return;
        }
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
            ? doc.getDocument().getBody().getSectPr()
            : doc.getDocument().getBody().addNewSectPr();
        applyPageGeometry(sectPr);
        // body/sectPr 对应最后一节（第一章起正文）
        applyPageNumberFormat(sectPr, resolveBodyPageNumberMode());
    }

    /**
     * 写入页眉（奇/偶）与正文页脚页码。
     * 目录节页脚在写入目录分节时挂载；摘要等前置节不挂页码。
     */
    private void applyHeaderAndFooter(XWPFDocument doc) {
        resetHeaderFooters(doc);
        applyConfiguredHeaders(doc);
        if (footerEnabled()) {
            XWPFFooter bodyFooter = createPageNumberFooter(doc, resolveBodyPageNumberMode() == PageNumberMode.ARABIC);
            CTSectPr bodySectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
            attachFooter(doc, bodySectPr, bodyFooter);
            attachConfiguredHeadersToSect(doc, bodySectPr);
        }
        else {
            CTSectPr bodySectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
            attachFooter(doc, bodySectPr, createEmptyFooter(doc));
            attachConfiguredHeadersToSect(doc, bodySectPr);
        }
    }

    private void applyConfiguredHeaders(XWPFDocument doc) {
        PaperFormatConfig.HeaderFooter hf = fmt().nestedHeaderFooter();
        String odd = hf == null ? null : StringUtils.trim(hf.getOddHeader());
        String even = hf == null ? null : StringUtils.trim(hf.getEvenHeader());
        if (StringUtils.isBlank(odd) && StringUtils.isBlank(even)) {
            return;
        }
        if (StringUtils.isNotBlank(odd) && StringUtils.isNotBlank(even) && !odd.equals(even)) {
            createTextHeader(doc, HeaderFooterType.DEFAULT, odd);
            createTextHeader(doc, HeaderFooterType.EVEN, even);
        }
        else {
            String text = StringUtils.isNotBlank(odd) ? odd : even;
            createTextHeader(doc, HeaderFooterType.DEFAULT, text);
        }
    }

    private XWPFHeader createTextHeader(XWPFDocument doc, HeaderFooterType type, String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        XWPFHeader header = doc.createHeader(type);
        header.clearHeaderFooter();
        XWPFParagraph para = header.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        para.setSpacingBefore(0);
        para.setSpacingAfter(0);
        XWPFRun run = para.createRun();
        applyFont(run, fontFooter(), fontSizeFooter());
        run.setText(text);
        return header;
    }

    private void attachConfiguredHeadersToSect(XWPFDocument doc, CTSectPr sectPr) {
        if (sectPr == null) {
            return;
        }
        // createHeader 通常已写入 body sectPr；各分节再补齐引用
        for (XWPFHeader header : doc.getHeaderList()) {
            String relId = doc.getRelationId(header);
            if (StringUtils.isBlank(relId)) {
                continue;
            }
            STHdrFtr.Enum type = headerTypeOf(doc, header);
            boolean exists = false;
            for (int i = 0; i < sectPr.sizeOfHeaderReferenceArray(); i++) {
                CTHdrFtrRef existing = sectPr.getHeaderReferenceArray(i);
                if (existing.getType() == type || relId.equals(existing.getId())) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                continue;
            }
            CTHdrFtrRef ref = sectPr.addNewHeaderReference();
            ref.setType(type);
            ref.setId(relId);
        }
    }

    private STHdrFtr.Enum headerTypeOf(XWPFDocument doc, XWPFHeader header) {
        try {
            var policy = doc.getHeaderFooterPolicy();
            if (policy != null && policy.getEvenPageHeader() == header) {
                return STHdrFtr.EVEN;
            }
            if (policy != null && policy.getFirstPageHeader() == header) {
                return STHdrFtr.FIRST;
            }
        } catch (Exception ignored) {
            // ignore
        }
        String packagePart = "";
        try {
            if (header.getPackagePart() != null && header.getPackagePart().getPartName() != null) {
                packagePart = header.getPackagePart().getPartName().getName().toLowerCase();
            }
        } catch (Exception ignored) {
            // ignore
        }
        if (packagePart.contains("even")) {
            return STHdrFtr.EVEN;
        }
        if (packagePart.contains("first")) {
            return STHdrFtr.FIRST;
        }
        return STHdrFtr.DEFAULT;
    }

    /** 创建页码页脚（支持 - n - 样式） */
    private XWPFFooter createPageNumberFooter(XWPFDocument doc, boolean arabicPlaceholder) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        footer.clearHeaderFooter();
        XWPFParagraph para = footer.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        para.setSpacingBefore(0);
        para.setSpacingAfter(0);
        appendPageNumberField(para, arabicPlaceholder, footerUsesDash());
        return footer;
    }

    /** 空页脚（摘要等前置页不显示页码） */
    private XWPFFooter createEmptyFooter(XWPFDocument doc) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        footer.clearHeaderFooter();
        footer.createParagraph();
        return footer;
    }

    /** 导出末尾再次锁定正文节：阿拉伯数字从 1、页脚（避免中途 createFooter 改写末节引用） */
    private void ensureBodySectionPageSetup(XWPFDocument doc) {
        CTSectPr bodySectPr = doc.getDocument().getBody().isSetSectPr()
            ? doc.getDocument().getBody().getSectPr()
            : doc.getDocument().getBody().addNewSectPr();
        Boolean enabled = fmt().getExport().getApplyPageSetup();
        if (enabled == null || enabled) {
            applyPageGeometry(bodySectPr);
        }
        applyPageNumberFormat(bodySectPr, resolveBodyPageNumberMode());
        if (bodySectPr.isSetTitlePg()) {
            bodySectPr.unsetTitlePg();
        }
        if (footerEnabled()) {
            attachFooter(doc, bodySectPr, createPageNumberFooter(doc, resolveBodyPageNumberMode() == PageNumberMode.ARABIC));
        }
        else {
            attachFooter(doc, bodySectPr, createEmptyFooter(doc));
        }
        attachConfiguredHeadersToSect(doc, bodySectPr);
    }

    private void attachFooter(XWPFDocument doc, CTSectPr sectPr, XWPFFooter footer) {
        if (sectPr == null || footer == null) {
            return;
        }
        // 去掉原 DEFAULT 页脚引用，避免多节串页脚
        for (int i = sectPr.sizeOfFooterReferenceArray() - 1; i >= 0; i--) {
            CTHdrFtrRef ref = sectPr.getFooterReferenceArray(i);
            if (ref.getType() == STHdrFtr.DEFAULT) {
                sectPr.removeFooterReference(i);
            }
        }
        String relId = doc.getRelationId(footer);
        if (StringUtils.isBlank(relId)) {
            return;
        }
        CTHdrFtrRef ref = sectPr.addNewFooterReference();
        ref.setType(STHdrFtr.DEFAULT);
        ref.setId(relId);
    }

    private void appendPageNumberField(XWPFParagraph paragraph, boolean arabic, boolean dash) {
        if (dash) {
            XWPFRun prefix = paragraph.createRun();
            applyFont(prefix, fontFooter(), fontSizeFooter());
            prefix.setText("- ");
        }
        XWPFRun run = paragraph.createRun();
        applyFont(run, fontFooter(), fontSizeFooter());
        CTR ctr = run.getCTR();

        CTFldChar begin = ctr.addNewFldChar();
        begin.setFldCharType(STFldCharType.BEGIN);

        CTText instr = ctr.addNewInstrText();
        // 格式由节属性 pgNumType 决定；域本身只用 PAGE
        instr.setStringValue(" PAGE ");

        CTFldChar separate = ctr.addNewFldChar();
        separate.setFldCharType(STFldCharType.SEPARATE);

        ctr.addNewT().setStringValue(arabic ? "1" : "I");

        CTFldChar end = ctr.addNewFldChar();
        end.setFldCharType(STFldCharType.END);

        if (dash) {
            XWPFRun suffix = paragraph.createRun();
            applyFont(suffix, fontFooter(), fontSizeFooter());
            suffix.setText(" -");
        }
    }

    private void applyPageGeometry(CTSectPr sectPr) {
        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSz.setW(BigInteger.valueOf(A4_WIDTH));
        pageSz.setH(BigInteger.valueOf(A4_HEIGHT));
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setLeft(BigInteger.valueOf(marginLeftTwips()));
        mar.setRight(BigInteger.valueOf(marginRightTwips()));
        mar.setTop(BigInteger.valueOf(marginTopTwips()));
        mar.setBottom(BigInteger.valueOf(marginBottomTwips()));
        mar.setHeader(BigInteger.valueOf(HEADER_DISTANCE));
        mar.setFooter(BigInteger.valueOf(FOOTER_DISTANCE));
    }

    private void applyPageNumberFormat(CTSectPr sectPr, PageNumberMode mode) {
        if (mode == PageNumberMode.NONE) {
            if (sectPr.isSetPgNumType()) {
                sectPr.unsetPgNumType();
            }
            return;
        }
        CTPageNumber pgNum = sectPr.isSetPgNumType() ? sectPr.getPgNumType() : sectPr.addNewPgNumType();
        if (mode == PageNumberMode.ROMAN) {
            pgNum.setFmt(STNumberFormat.UPPER_ROMAN);
        } else {
            pgNum.setFmt(STNumberFormat.DECIMAL);
        }
        pgNum.setStart(BigInteger.ONE);
    }

    /**
     * 覆盖模板 styles.xml 中的字号/字体（模板 Normal 多为五号，中文实际读 szCs）。
     * 仅作用于本次导出的 docx 副本，不写回磁盘模板。
     */
    private void patchTemplateStyles(XWPFDocument doc) {
        Boolean patch = fmt().getExport().getPatchTemplateStyles();
        if (patch != null && !patch) {
            return;
        }
        PaperTemplateStyleMapping mapping = currentTemplateStyles();
        if (mapping == null) {
            return;
        }
        PaperFormatConfig.Heading heading = fmt().getHeading();
        patchStyleFont(doc, mapping.getNormal(), fontBody(), fontSizeBody(), false);
        patchStyleFirstLineIndent(doc, mapping.getNormal());
        patchStyleFont(doc, mapping.getReference(), fontReference(), fontSizeReference(), false);
        // 参考文献禁止两端/分散对齐，否则短行会被 Word 拉大字距
        patchStyleAlignment(doc, mapping.getReference(), STJc.LEFT);
        // 模板「参考文献」样式自带自动编号 lvlText=[%1]，与正文手写 [n] 叠成 [1][1]
        patchStyleClearNumbering(doc, mapping.getReference());
        patchStyleClearIndent(doc, mapping.getReference());
        patchStyleFont(doc, mapping.getHeading1(), fontHeadingAt(1), fontSizeH1(),
            Boolean.TRUE.equals(heading.getH1Bold()));
        patchStyleFont(doc, mapping.getHeading2(), fontHeadingAt(2), fontSizeH2(),
            Boolean.TRUE.equals(heading.getH2Bold()));
        patchStyleFont(doc, mapping.getHeading3(), fontHeadingAt(3), fontSizeH3(),
            Boolean.TRUE.equals(heading.getH3Bold()));
        // 标题样式常基于正文，会继承首行缩进；强制顶格
        patchStyleClearIndent(doc, mapping.getHeading1());
        patchStyleClearIndent(doc, mapping.getHeading2());
        patchStyleClearIndent(doc, mapping.getHeading3());
        patchStyleTocTab(doc, mapping.getToc1());
        patchStyleTocTab(doc, mapping.getToc2());
        patchStyleTocTab(doc, mapping.getToc3());
    }

    /** 为 TOC 样式补右对齐点线制表位（模板 toc 样式通常无 leader，静态目录需显式设置） */
    private void patchStyleTocTab(XWPFDocument doc, String styleId) {
        if (StringUtils.isBlank(styleId)) {
            return;
        }
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return;
        }
        XWPFStyle style = styles.getStyle(styleId);
        if (style == null || style.getCTStyle() == null) {
            return;
        }
        CTPPrGeneral pPr = style.getCTStyle().isSetPPr()
            ? style.getCTStyle().getPPr()
            : style.getCTStyle().addNewPPr();
        if (pPr.isSetTabs()) {
            pPr.unsetTabs();
        }
        CTTabs tabs = pPr.addNewTabs();
        CTTabStop tab = tabs.addNewTab();
        tab.setVal(STTabJc.RIGHT);
        tab.setLeader(STTabTlc.DOT);
        tab.setPos(BigInteger.valueOf(tocTabPos()));
    }

    private void patchStyleFont(XWPFDocument doc, String styleId, String family, double sizePt, boolean bold) {
        if (StringUtils.isBlank(styleId)) {
            return;
        }
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return;
        }
        XWPFStyle style = styles.getStyle(styleId);
        if (style == null || style.getCTStyle() == null) {
            return;
        }
        CTRPr rPr = style.getCTStyle().isSetRPr()
            ? style.getCTStyle().getRPr()
            : style.getCTStyle().addNewRPr();
        applyRunFontProperties(rPr, family, sizePt);
        if (bold) {
            if (rPr.sizeOfBArray() == 0) {
                rPr.addNewB();
            }
        } else if (rPr.sizeOfBArray() > 0) {
            rPr.getBArray(0).setVal(false);
        }
    }

    /** 覆盖模板段落对齐（参考文献忌用两端/分散对齐） */
    private void patchStyleAlignment(XWPFDocument doc, String styleId, STJc.Enum alignment) {
        if (StringUtils.isBlank(styleId) || alignment == null) {
            return;
        }
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return;
        }
        XWPFStyle style = styles.getStyle(styleId);
        if (style == null || style.getCTStyle() == null) {
            return;
        }
        CTPPrGeneral pPr = style.getCTStyle().isSetPPr()
            ? style.getCTStyle().getPPr()
            : style.getCTStyle().addNewPPr();
        if (pPr.isSetJc()) {
            pPr.getJc().setVal(alignment);
        } else {
            pPr.addNewJc().setVal(alignment);
        }
    }

    /** 去掉样式上的自动编号（参考文献序号改由正文文本写入） */
    private void patchStyleClearNumbering(XWPFDocument doc, String styleId) {
        if (StringUtils.isBlank(styleId)) {
            return;
        }
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return;
        }
        XWPFStyle style = styles.getStyle(styleId);
        if (style == null || style.getCTStyle() == null) {
            return;
        }
        CTPPrGeneral pPr = style.getCTStyle().isSetPPr()
            ? style.getCTStyle().getPPr()
            : style.getCTStyle().addNewPPr();
        if (pPr.isSetNumPr()) {
            pPr.unsetNumPr();
        }
    }

    /** 清除样式缩进，保证参考文献序号/各级标题左顶格 */
    private void patchStyleClearIndent(XWPFDocument doc, String styleId) {
        if (StringUtils.isBlank(styleId)) {
            return;
        }
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return;
        }
        XWPFStyle style = styles.getStyle(styleId);
        if (style == null || style.getCTStyle() == null) {
            return;
        }
        CTPPrGeneral pPr = style.getCTStyle().isSetPPr()
            ? style.getCTStyle().getPPr()
            : style.getCTStyle().addNewPPr();
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setLeft(BigInteger.ZERO);
        ind.setRight(BigInteger.ZERO);
        ind.setFirstLine(BigInteger.ZERO);
        ind.setFirstLineChars(BigInteger.ZERO);
        if (ind.isSetLeftChars()) {
            ind.unsetLeftChars();
        }
        if (ind.isSetRightChars()) {
            ind.unsetRightChars();
        }
        if (ind.isSetHanging()) {
            ind.unsetHanging();
        }
        if (ind.isSetHangingChars()) {
            ind.unsetHangingChars();
        }
    }

    /** 为模板 Normal 等正文样式补首行缩进 2 字符（WPS/Word 均认 firstLineChars） */
    private void patchStyleFirstLineIndent(XWPFDocument doc, String styleId) {
        if (StringUtils.isBlank(styleId)) {
            return;
        }
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return;
        }
        XWPFStyle style = styles.getStyle(styleId);
        if (style == null || style.getCTStyle() == null) {
            return;
        }
        CTPPrGeneral pPr = style.getCTStyle().isSetPPr()
            ? style.getCTStyle().getPPr()
            : style.getCTStyle().addNewPPr();
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setFirstLineChars(BigInteger.valueOf(bodyFirstLineCharsHundredths()));
        ind.setFirstLine(BigInteger.valueOf(bodyFirstLineTwips()));
    }

    private void applyBodyFirstLineIndent(XWPFParagraph paragraph) {
        CTPPr pPr = paragraph.getCTP().isSetPPr()
            ? paragraph.getCTP().getPPr()
            : paragraph.getCTP().addNewPPr();
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setFirstLineChars(BigInteger.valueOf(bodyFirstLineCharsHundredths()));
        ind.setFirstLine(BigInteger.valueOf(bodyFirstLineTwips()));
    }

    private void applyBodyParagraphLayout(XWPFParagraph paragraph) {
        PaperFormatConfig.Paragraph para = fmt().getParagraph();
        String rule = para.getLineSpacingRule();
        if (rule != null && "auto".equalsIgnoreCase(rule.trim())) {
            double multiple = firstNonNull(para.getLineSpacingMultiple(), 1.5);
            paragraph.setSpacingBetween(multiple, LineSpacingRule.AUTO);
        } else {
            double pt = firstNonNull(para.getLineSpacingPt(), BODY_LINE_SPACING_PT);
            paragraph.setSpacingBetween(pt, LineSpacingRule.EXACT);
        }
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setAlignment(resolveAlign(para.getBodyAlign(), ParagraphAlignment.BOTH));
        applyBodyFirstLineIndent(paragraph);
    }

    /**
     * 取论文标题（用于文件名），缺省「论文」。
     */
    public String resolveTitle(String sessionId) {
        PaperSession session = paperSessionStore.get(sessionId);
        String title = session == null ? null : session.getTitle();
        return StringUtils.isBlank(title) ? "论文" : title.trim();
    }

    // ---------------- 页面 / 标题 ----------------

    /** 注册 Heading1-3 样式（含 outlineLvl），使 Word 左侧导航与自动目录可识别章节结构 */
    private void ensureDocumentStyles(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles();
        addHeadingStyle(styles, STYLE_HEADING1, "heading 1", 0, fontSizeH1());
        addHeadingStyle(styles, STYLE_HEADING2, "heading 2", 1, fontSizeH2());
        addHeadingStyle(styles, STYLE_HEADING3, "heading 3", 2, fontSizeH3());
        addSectionTitleStyle(styles);
    }

    private void addHeadingStyle(XWPFStyles styles, String styleId, String name, int outlineLevel, double fontSize) {
        CTStyle ctStyle = CTStyle.Factory.newInstance();
        ctStyle.setStyleId(styleId);
        CTString styleName = CTString.Factory.newInstance();
        styleName.setVal(name);
        ctStyle.setName(styleName);

        CTOnOff onOff = CTOnOff.Factory.newInstance();
        ctStyle.setQFormat(onOff);
        ctStyle.setUnhideWhenUsed(onOff);

        CTPPrGeneral pPr = ctStyle.addNewPPr();
        pPr.addNewOutlineLvl().setVal(BigInteger.valueOf(outlineLevel));

        CTRPr rPr = ctStyle.addNewRPr();
        applyRunFontProperties(rPr, fontHeading(), fontSize);

        XWPFStyle style = new XWPFStyle(ctStyle);
        style.setType(STStyleType.PARAGRAPH);
        styles.addStyle(style);
    }

    /** 摘要/参考文献等不参与目录编号的节标题 */
    private void addSectionTitleStyle(XWPFStyles styles) {
        CTStyle ctStyle = CTStyle.Factory.newInstance();
        ctStyle.setStyleId(STYLE_SECTION_TITLE);
        CTString styleName = CTString.Factory.newInstance();
        styleName.setVal("section title");
        ctStyle.setName(styleName);

        CTRPr rPr = ctStyle.addNewRPr();
        applyRunFontProperties(rPr, fontHeading(), fontSizeH1());

        XWPFStyle style = new XWPFStyle(ctStyle);
        style.setType(STStyleType.PARAGRAPH);
        styles.addStyle(style);
    }

    private void writeTitle(XWPFDocument doc, String title, PaperTemplateStyleMapping templateStyles) {
        if (StringUtils.isBlank(title)) {
            return;
        }
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(240);
        if (templateStyles != null && StringUtils.isNotBlank(templateStyles.getHeading1())) {
            p.setStyle(templateStyles.getHeading1());
        }
        XWPFRun run = p.createRun();
        run.setBold(!Boolean.FALSE.equals(fmt().getHeading().getTitleBold()));
        applyFont(run, fontHeading(), fontSizeTitle());
        run.setText(title.trim());
    }

    // ---------------- 摘要（中英文分页） ----------------

    private record AbstractSplit(String chinese, String englishTitle, String englishBody) {}

    private void renderAbstractChapter(XWPFDocument doc, String paperTitle, String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        AbstractSplit parts = splitAbstractContent(content);
        addAbstractSectionLabel(doc, "摘要", true);
        renderAbstractTextBlock(doc, parts.chinese(), true);
        addPageBreak(doc);

        if (StringUtils.isBlank(parts.englishBody()) && StringUtils.isBlank(parts.englishTitle())) {
            return;
        }
        String enTitle = resolveEnglishPaperTitle(parts.englishTitle(), parts.englishBody(), paperTitle);
        String englishBody = stripDuplicateEnglishTitle(parts.englishBody(), enTitle);
        if (StringUtils.isNotBlank(enTitle)) {
            addEnglishPaperTitle(doc, enTitle);
        }
        addAbstractSectionLabel(doc, "Abstract", false);
        renderAbstractTextBlock(doc, englishBody, false);
    }

    private String resolveEnglishPaperTitle(String extracted, String englishBody, String paperTitle) {
        if (StringUtils.isNotBlank(extracted)) {
            return extracted.trim();
        }
        String fromBody = peekEnglishTitleFromBody(englishBody);
        if (StringUtils.isNotBlank(fromBody)) {
            return fromBody;
        }
        if (StringUtils.isNotBlank(paperTitle) && isMostlyLatin(paperTitle)) {
            return paperTitle.trim();
        }
        return null;
    }

    private String stripDuplicateEnglishTitle(String body, String englishTitle) {
        if (StringUtils.isBlank(body) || StringUtils.isBlank(englishTitle)) {
            return body;
        }
        String rest = body.trim();
        String firstLine = rest.lines().findFirst().orElse("").trim();
        if (titlesEquivalent(firstLine, englishTitle)) {
            int cut = rest.indexOf('\n');
            return cut >= 0 ? rest.substring(cut + 1).trim() : "";
        }
        return rest;
    }

    private String peekEnglishTitleFromBody(String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        for (String line : body.split("\n")) {
            String trim = line.strip();
            if (trim.isEmpty() || ABSTRACT_HEADER.matcher(trim).matches() || ABSTRACT_LINE.matcher(trim).matches()) {
                continue;
            }
            if (ENGLISH_KEYWORDS.matcher(trim).find()) {
                break;
            }
            if (looksLikeEnglishPaperTitle(trim)) {
                return trim;
            }
            break;
        }
        return null;
    }

    private boolean looksLikeEnglishPaperTitle(String line) {
        if (StringUtils.isBlank(line) || line.length() > 200) {
            return false;
        }
        if (ENGLISH_KEYWORDS.matcher(line).find() || CHINESE_KEYWORDS.matcher(line).find()) {
            return false;
        }
        if (!isMostlyLatin(line)) {
            return false;
        }
        long words = line.trim().split("\\s+").length;
        return words >= 3 && words <= 30;
    }

    private boolean titlesEquivalent(String a, String b) {
        if (StringUtils.isBlank(a) || StringUtils.isBlank(b)) {
            return false;
        }
        return normalizeTitleKey(a).equals(normalizeTitleKey(b));
    }

    private String normalizeTitleKey(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
    }

    private boolean isMostlyLatin(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        int latin = 0;
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            total++;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                latin++;
            }
        }
        return total > 0 && latin * 100 / total >= 60;
    }

    private AbstractSplit splitAbstractContent(String raw) {
        String cleaned = raw.replace("（正在生成英文 ABSTRACT，请稍候…）", "")
            .replaceAll("(?m)^摘要\\s*$", "")
            .trim();
        int markerIdx = findAbstractMarkerIndex(cleaned);
        if (markerIdx < 0) {
            markerIdx = findEnglishSectionStartIndex(cleaned);
        }
        if (markerIdx < 0) {
            return new AbstractSplit(cleaned, null, null);
        }

        String before = cleaned.substring(0, markerIdx).trim();
        String after = cleaned.substring(markerIdx).trim();
        after = stripLeadingAbstractMarkerLines(after);

        String chinese;
        String englishTitle = null;
        Matcher kw = CHINESE_KEYWORDS.matcher(before);
        if (kw.find()) {
            int kwLineEnd = before.indexOf('\n', kw.start());
            int splitAt = kwLineEnd >= 0 ? kwLineEnd + 1 : before.length();
            chinese = before.substring(0, splitAt).trim();
            englishTitle = collectEnglishTitleLines(before.substring(splitAt));
        } else {
            chinese = before;
        }

        if (StringUtils.isBlank(englishTitle)) {
            englishTitle = collectEnglishTitleLines(after);
            if (StringUtils.isNotBlank(englishTitle)) {
                after = stripDuplicateEnglishTitle(after, englishTitle);
            }
        }

        return new AbstractSplit(chinese, englishTitle, after);
    }

    private int findEnglishSectionStartIndex(String text) {
        Matcher kw = CHINESE_KEYWORDS.matcher(text);
        if (!kw.find()) {
            return -1;
        }
        int pos = kw.start();
        int lineEnd = text.indexOf('\n', pos);
        pos = lineEnd >= 0 ? lineEnd + 1 : text.length();
        while (pos < text.length()) {
            int nextEnd = text.indexOf('\n', pos);
            String line = (nextEnd >= 0 ? text.substring(pos, nextEnd) : text.substring(pos)).strip();
            if (line.isEmpty()) {
                pos = nextEnd >= 0 ? nextEnd + 1 : text.length();
                continue;
            }
            if (looksLikeEnglishPaperTitle(line) || (isMostlyLatin(line) && !ENGLISH_KEYWORDS.matcher(line).find())) {
                return pos;
            }
            break;
        }
        return -1;
    }

    private int findAbstractMarkerIndex(String text) {
        Matcher header = ABSTRACT_HEADER.matcher(text);
        if (header.find()) {
            return header.start();
        }
        Matcher line = ABSTRACT_LINE.matcher(text);
        return line.find() ? line.start() : -1;
    }

    private String stripLeadingAbstractMarkerLines(String text) {
        String rest = text == null ? "" : text.trim();
        while (!rest.isEmpty()) {
            int lineEnd = rest.indexOf('\n');
            String first = lineEnd >= 0 ? rest.substring(0, lineEnd).trim() : rest.trim();
            if (ABSTRACT_HEADER.matcher(first).matches() || ABSTRACT_LINE.matcher(first).matches()) {
                rest = lineEnd >= 0 ? rest.substring(lineEnd + 1).trim() : "";
                continue;
            }
            break;
        }
        return rest;
    }

    private String collectEnglishTitleLines(String tail) {
        if (StringUtils.isBlank(tail)) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (String line : tail.split("\n")) {
            String trim = line.strip();
            if (trim.isEmpty()) {
                if (!lines.isEmpty()) {
                    break;
                }
                continue;
            }
            if (ABSTRACT_HEADER.matcher(trim).matches() || ABSTRACT_LINE.matcher(trim).matches()) {
                break;
            }
            if (looksLikeEnglishPaperTitle(trim) || (lines.isEmpty() && isMostlyLatin(trim) && trim.length() <= 200)) {
                lines.add(trim);
                if (lines.size() >= 2) {
                    break;
                }
                continue;
            }
            if (lines.isEmpty()) {
                lines.add(trim);
            }
            break;
        }
        if (lines.isEmpty()) {
            return null;
        }
        return String.join(" ", lines);
    }

    /** 「摘要」「Abstract:」：行首左对齐、加粗、小四 */
    private void addAbstractSectionLabel(XWPFDocument doc, String label, boolean chinese) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        p.setSpacingBefore(chinese ? 80 : 160);
        p.setSpacingAfter(80);
        p.setSpacingBetween(1.5, LineSpacingRule.AUTO);
        XWPFRun run = p.createRun();
        run.setBold(true);
        if (chinese) {
            applyFont(run, fontAbstract(), fontSizeAbstractLabel());
        } else {
            applyFont(run, fontTableAscii(), fontSizeAbstractLabel());
        }
        run.setText(chinese ? label : "Abstract:");
    }

    /** 英文摘要页题目：Times New Roman 小二、居中、加粗 */
    private void addEnglishPaperTitle(XWPFDocument doc, String title) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(120);
        p.setSpacingBetween(1.5, LineSpacingRule.AUTO);
        XWPFRun run = p.createRun();
        run.setBold(true);
        applyFont(run, fontTableAscii(), fontSizeTitle());
        run.setText(title);
    }

    private void renderAbstractTextBlock(XWPFDocument doc, String block, boolean chinese) {
        if (StringUtils.isBlank(block)) {
            return;
        }
        for (String paragraph : splitAbstractParagraphs(block, chinese)) {
            String trim = paragraph.strip();
            if (trim.isEmpty()) {
                continue;
            }
            if (!chinese && (ABSTRACT_HEADER.matcher(trim).matches() || ABSTRACT_LINE.matcher(trim).matches())) {
                continue;
            }
            if (CHINESE_KEYWORDS.matcher(trim).find()) {
                addAbstractKeywordsLine(doc, trim, true);
            } else if (ENGLISH_KEYWORDS.matcher(trim).find()) {
                addAbstractKeywordsLine(doc, trim, false);
            } else {
                addAbstractBodyParagraph(doc, trim, chinese);
            }
        }
    }

    /** 摘要按空行分段；段内单行换行合并为一段，避免每行都首行缩进 2 字符 */
    private List<String> splitAbstractParagraphs(String block, boolean chinese) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : block.replace("\r\n", "\n").split("\n")) {
            String trim = line.strip();
            if (trim.isEmpty()) {
                flushAbstractParagraph(current, paragraphs);
                continue;
            }
            if (current.length() > 0) {
                if (chinese) {
                    current.append(trim);
                } else {
                    current.append(' ').append(trim);
                }
            } else {
                current.append(trim);
            }
        }
        flushAbstractParagraph(current, paragraphs);
        return paragraphs;
    }

    private void flushAbstractParagraph(StringBuilder current, List<String> paragraphs) {
        if (current.length() > 0) {
            paragraphs.add(current.toString().trim());
            current.setLength(0);
        }
    }

    private void addAbstractBodyParagraph(XWPFDocument doc, String text, boolean chinese) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        text = stripLeadingParagraphIndent(text);
        XWPFParagraph p = doc.createParagraph();
        applyBodyParagraphLayout(p);
        XWPFRun run = p.createRun();
        applyFont(run, chinese ? fontAbstract() : fontTableAscii(), fontSizeAbstractBody());
        run.setText(text);
    }

    private void addAbstractKeywordsLine(XWPFDocument doc, String line, boolean chinese) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(120);
        p.setSpacingBetween(1.5, LineSpacingRule.AUTO);
        int sep = indexOfKeywordSeparator(line);
        if (sep < 0) {
            XWPFRun run = p.createRun();
            run.setBold(true);
            applyFont(run, chinese ? fontKeyword() : fontTableAscii(), fontSizeKeyword());
            run.setText(line);
            return;
        }
        String label = line.substring(0, sep);
        String rest = line.substring(sep);
        XWPFRun labelRun = p.createRun();
        labelRun.setBold(true);
        applyFont(labelRun, chinese ? fontKeyword() : fontTableAscii(), fontSizeKeyword());
        labelRun.setText(label);
        XWPFRun restRun = p.createRun();
        applyFont(restRun, chinese ? fontKeyword() : fontTableAscii(), fontSizeKeyword());
        restRun.setText(rest);
    }

    private int indexOfKeywordSeparator(String line) {
        int cn = line.indexOf('：');
        if (cn >= 0) {
            return cn + 1;
        }
        int en = line.indexOf(':');
        return en >= 0 ? en + 1 : -1;
    }

    // ---------------- 正文组装 ----------------

    private void writeBody(XWPFDocument doc, PaperSession session) {
        List<TocNode> toc = session.getToc();
        if (toc == null || toc.isEmpty()) {
            // 无目录时直接按已生成内容顺序输出
            Map<String, String> generated = session.getGeneratedContent();
            if (generated != null) {
                generated.values().forEach(content -> renderContent(doc, content));
            }
            return;
        }
        Map<String, Integer> headingPages = layoutEstimator.estimate(toc, session);

        boolean tocPageInserted = false;
        // 目录分节后第一章已在新页；此后每个大章节强制换页，避免与上一章同页
        boolean needMajorChapterPageBreak = false;
        for (TocNode node : toc) {
            if (isTocPageNode(node)) {
                continue;
            }
            if (!tocPageInserted && !isAbstractNode(node)) {
                writeTableOfContentsPage(doc, toc, headingPages);
                tocPageInserted = true;
            }
            if (needMajorChapterPageBreak && !isAbstractNode(node)) {
                addPageBreak(doc);
            }
            writeNode(doc, node, session);
            if (!tocPageInserted && isAbstractNode(node)) {
                writeTableOfContentsPage(doc, toc, headingPages);
                tocPageInserted = true;
            }
            if (!isAbstractNode(node)) {
                needMajorChapterPageBreak = true;
            }
        }
    }

    private void writeNode(XWPFDocument doc, TocNode node, PaperSession session) {
        int level = node.getLevel() == null ? 1 : node.getLevel();
        String title = node.getTitle() == null ? node.getId() : node.getTitle();

        String content = session.getGeneratedContent() == null ? null
            : session.getGeneratedContent().get(node.getId());

        if (isAbstractNode(node)) {
            renderAbstractChapter(doc, session.getTitle(), content);
        } else if (isReferenceNode(node)) {
            addSectionTitle(doc, title);
            // 与预览一致：优先用参考文献章节正文；无正文时再回退结构化列表
            if (StringUtils.isNotBlank(content) && !isReferencePlaceholder(content)) {
                renderReferenceContent(doc, content);
            } else if (session.getReferences() != null && !session.getReferences().isEmpty()) {
                renderReferences(doc, session.getReferences());
            }
        } else if (isAcknowledgmentNode(node)) {
            addHeading(doc, title, level);
            acknowledgmentBody.set(Boolean.TRUE);
            try {
                if (StringUtils.isNotBlank(content)) {
                    renderContent(doc, content);
                }
            } finally {
                acknowledgmentBody.remove();
            }
        } else {
            addHeading(doc, title, level);
            if (StringUtils.isNotBlank(content)) {
                renderContent(doc, content);
            }
        }

        if (node.getChildren() != null) {
            for (TocNode child : node.getChildren()) {
                writeNode(doc, child, session);
            }
        }
    }

    private boolean isReferenceNode(TocNode node) {
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        String title = node.getTitle() == null ? "" : node.getTitle();
        return id.contains("reference") || title.contains("参考文献");
    }

    private boolean isAcknowledgmentNode(TocNode node) {
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        String title = node.getTitle() == null ? "" : node.getTitle();
        return id.contains("ack") || title.contains("致谢") || title.contains("鸣谢");
    }

    /** 占位提示不算有效参考文献正文 */
    private boolean isReferencePlaceholder(String content) {
        String t = content == null ? "" : content.strip();
        return t.isEmpty() || t.contains("暂无参考文献");
    }

    private boolean isAbstractNode(TocNode node) {
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        String title = node.getTitle() == null ? "" : node.getTitle();
        return "abstract".equals(id) || title.contains("摘要");
    }

    /** 大纲中的「目录」节点由导出时自动生成，跳过重复写入 */
    private boolean isTocPageNode(TocNode node) {
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        String title = node.getTitle() == null ? "" : node.getTitle();
        return id.equals("toc") || id.equals("catalog") || title.equals("目录");
    }

    /** 摘要、参考文献等不出现在正文目录页 */
    private boolean isExcludedFromTocPage(TocNode node) {
        return isAbstractNode(node) || isTocPageNode(node) || isReferenceNode(node);
    }

    /**
     * 摘要之后插入目录页。
     * <p>Word 节属性写在「本节末尾」：先结束前置节（无页码），再写目录，再结束目录节（罗马数字从 I 起）；
     * 文档末节（body/sectPr）为正文阿拉伯数字从 1 起。
     */
    private void writeTableOfContentsPage(XWPFDocument doc, List<TocNode> toc, Map<String, Integer> headingPages) {
        // 结束题目/摘要节：不编页码；下一页起为目录
        addSectionBreak(doc, PageNumberMode.NONE, false);

        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        titlePara.setSpacingAfter(200);
        XWPFRun titleRun = titlePara.createRun();
        applyFont(titleRun, fontHeading(), fontSizeH1());
        titleRun.setBold(false);
        titleRun.setText("目录");

        for (TocNode node : toc) {
            appendStaticTocEntry(doc, node, headingPages);
        }

        // 结束目录节：罗马数字从 I 起；下一页起第一章（阿拉伯）
        addSectionBreak(doc, PageNumberMode.ROMAN, true);
    }

    /**
     * 插入下一页分节符。sectPr 描述的是「即将结束的本节」，不是下一节。
     *
     * @param mode           本节页码格式
     * @param withPageFooter 本节是否显示页码页脚
     */
    private void addSectionBreak(XWPFDocument doc, PageNumberMode mode, boolean withPageFooter) {
        XWPFParagraph p = doc.createParagraph();
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTSectPr sectPr = pPr.isSetSectPr() ? pPr.getSectPr() : pPr.addNewSectPr();
        if (sectPr.isSetType()) {
            sectPr.getType().setVal(STSectionMark.NEXT_PAGE);
        } else {
            sectPr.addNewType().setVal(STSectionMark.NEXT_PAGE);
        }
        applyPageGeometry(sectPr);
        applyPageNumberFormat(sectPr, mode);
        if (withPageFooter && footerEnabled()) {
            attachFooter(doc, sectPr, createPageNumberFooter(doc, mode == PageNumberMode.ARABIC));
        } else {
            attachFooter(doc, sectPr, createEmptyFooter(doc));
        }
        attachConfiguredHeadersToSect(doc, sectPr);
    }

    private void appendStaticTocEntry(XWPFDocument doc, TocNode node, Map<String, Integer> headingPages) {
        if (isExcludedFromTocPage(node)) {
            if (node.getChildren() != null) {
                for (TocNode child : node.getChildren()) {
                    appendStaticTocEntry(doc, child, headingPages);
                }
            }
            return;
        }
        int page = headingPages.getOrDefault(node.getId(), 1);
        addTocEntryLine(doc, node, page);
        if (node.getChildren() != null) {
            for (TocNode child : node.getChildren()) {
                appendStaticTocEntry(doc, child, headingPages);
            }
        }
    }

    private void addTocEntryLine(XWPFDocument doc, TocNode node, int pageNumber) {
        int level = node.getLevel() == null ? 1 : node.getLevel();
        String title = node.getTitle() == null ? node.getId() : node.getTitle();
        if (StringUtils.isBlank(title)) {
            return;
        }

        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        XWPFParagraph p = doc.createParagraph();
        if (templateStyles != null) {
            p.setStyle(templateStyles.tocStyleId(level));
        } else {
            p.setSpacingBetween(1.5, LineSpacingRule.AUTO);
            int indentTwips = Math.max(0, level - 1) * 420;
            if (indentTwips > 0) {
                p.setIndentationLeft(indentTwips);
            }
        }

        applyTocTabStops(p);

        XWPFRun run = p.createRun();
        if (templateStyles == null) {
            applyFont(run, fontBody(), fontSizeBody());
        }
        run.setText(title + "\t" + pageNumber);
    }

    /** 段落级点线制表位（覆盖样式缺 leader 的情况，保证标题与页码间虚线） */
    private void applyTocTabStops(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        if (pPr.isSetTabs()) {
            pPr.unsetTabs();
        }
        CTTabs tabs = pPr.addNewTabs();
        CTTabStop tab = tabs.addNewTab();
        tab.setVal(STTabJc.RIGHT);
        tab.setLeader(STTabTlc.DOT);
        tab.setPos(BigInteger.valueOf(tocTabPos()));
    }

    private void addPageBreak(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.addBreak(BreakType.PAGE);
    }

    /**
     * 渲染参考文献列表（GB/T 7714，左对齐 + 悬挂缩进）。
     * 禁止两端对齐/分散对齐，否则换行后的短行会被 Word 拉大字距。
     */
    private void renderReferences(XWPFDocument doc, List<Reference> references) {
        if (references == null || references.isEmpty()) {
            return;
        }
        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        int seq = 1;
        for (Reference ref : references) {
            addReferenceParagraph(doc, formatReferenceExportLine(ref, seq++), templateStyles);
        }
    }

    /** 统一「[n] + 题录」；若 citation 已含序号则先剥掉，避免与外层序号叠层 */
    private String formatReferenceExportLine(Reference ref, int sequentialIndex) {
        String body = StringUtils.isNotBlank(ref.getCitation()) ? ref.getCitation().trim() : "";
        body = stripLeadingReferenceIndex(body);
        return "[" + sequentialIndex + "] " + body;
    }

    private static final Pattern LEADING_REF_INDEX = Pattern.compile("^\\[\\s*\\d+\\s*]\\s*");
    /** 预览/脏数据可能把多条文献挤成一行，按 [n] 边界切分 */
    private static final Pattern REF_ENTRY_SPLIT = Pattern.compile("(?=\\[\\s*\\d+\\s*])");

    static String stripLeadingReferenceIndex(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String s = text.trim();
        // 连续前缀也清掉（历史脏数据 [1] [1] 作者…）
        while (true) {
            var m = LEADING_REF_INDEX.matcher(s);
            if (!m.find()) {
                break;
            }
            s = s.substring(m.end()).trim();
        }
        return s;
    }

    /** 从章节正文文本渲染参考文献（与预览同源） */
    private void renderReferenceContent(XWPFDocument doc, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        int autoIndex = 1;
        for (String entry : splitReferenceEntries(text)) {
            String trim = entry.strip();
            if (trim.isEmpty() || isReferencePlaceholder(trim)) {
                continue;
            }
            if (trim.startsWith("（") || trim.startsWith("(")) {
                continue;
            }
            String body = stripLeadingReferenceIndex(stripMarkdown(trim));
            if (body.isEmpty()) {
                continue;
            }
            addReferenceParagraph(doc, "[" + (autoIndex++) + "] " + body, templateStyles);
        }
    }

    /** 按换行切分；若挤成一行则按 [n] 边界再切 */
    private static List<String> splitReferenceEntries(String text) {
        List<String> entries = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        for (String line : normalized.split("\n")) {
            String trim = line.strip();
            if (trim.isEmpty()) {
                continue;
            }
            if (countLeadingRefMarkers(trim) <= 1) {
                entries.add(trim);
                continue;
            }
            for (String part : REF_ENTRY_SPLIT.split(trim)) {
                if (StringUtils.isNotBlank(part)) {
                    entries.add(part.strip());
                }
            }
        }
        return entries;
    }

    private static int countLeadingRefMarkers(String text) {
        Matcher m = Pattern.compile("\\[\\s*\\d+\\s*]").matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private void addReferenceParagraph(XWPFDocument doc, String text, PaperTemplateStyleMapping templateStyles) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        XWPFParagraph p = doc.createParagraph();
        // 不用模板「参考文献」样式：该样式与 numbering lvlText=[%1] 双向绑定，套用会再出一层序号
        if (templateStyles != null && StringUtils.isNotBlank(templateStyles.getNormal())) {
            p.setStyle(templateStyles.getNormal());
        }
        applyReferenceParagraphLayout(p);
        XWPFRun run = p.createRun();
        applyFont(run, fontReference(), fontSizeReference());
        run.setText(text);
    }

    private void applyReferenceParagraphLayout(XWPFParagraph paragraph) {
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        PaperFormatConfig.Paragraph para = fmt().getParagraph();
        String rule = para.getLineSpacingRule();
        if (rule != null && "auto".equalsIgnoreCase(rule.trim())) {
            double multiple = firstNonNull(para.getLineSpacingMultiple(), 1.5);
            paragraph.setSpacingBetween(multiple, LineSpacingRule.AUTO);
        } else {
            double pt = firstNonNull(para.getLineSpacingPt(), BODY_LINE_SPACING_PT);
            paragraph.setSpacingBetween(pt, LineSpacingRule.EXACT);
        }
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        CTPPr pPr = paragraph.getCTP().isSetPPr()
            ? paragraph.getCTP().getPPr()
            : paragraph.getCTP().addNewPPr();
        // numId=0：显式关闭自动编号（仅 unset 时 Word 仍可能因样式关联补回）
        if (pPr.isSetNumPr()) {
            pPr.unsetNumPr();
        }
        var numPr = pPr.addNewNumPr();
        numPr.addNewIlvl().setVal(BigInteger.ZERO);
        numPr.addNewNumId().setVal(BigInteger.ZERO);
        if (pPr.isSetJc()) {
            pPr.getJc().setVal(STJc.LEFT);
        } else {
            pPr.addNewJc().setVal(STJc.LEFT);
        }
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        // 序号左顶格：清除模板/正文带来的左缩进与首行缩进
        ind.setLeft(BigInteger.ZERO);
        ind.setRight(BigInteger.ZERO);
        if (ind.isSetFirstLine()) {
            ind.unsetFirstLine();
        }
        if (ind.isSetFirstLineChars()) {
            ind.unsetFirstLineChars();
        }
        if (ind.isSetHanging()) {
            ind.unsetHanging();
        }
        if (ind.isSetHangingChars()) {
            ind.unsetHangingChars();
        }
        // 显式 firstLine=0，防止继承样式仍缩进
        ind.setFirstLine(BigInteger.ZERO);
        ind.setFirstLineChars(BigInteger.ZERO);
    }

    /**
     * 渲染章节正文：支持 Markdown 表格、代码块、图/表标题居中、角标上标。
     */
    private void renderContent(XWPFDocument doc, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        boolean inCode = false;
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trim = line.strip();

            java.util.regex.Matcher mdImg = MARKDOWN_IMAGE.matcher(trim);
            if (mdImg.matches()) {
                addImageFromSrc(doc, mdImg.group(2).trim(), mdImg.group(1).trim());
                i++;
                continue;
            }

            // 跳过插图编辑元数据（[[[PAPER_DRAW:...]]] 等），勿写入 Word
            if (isPaperDrawMarkerStart(trim)) {
                i = skipPaperDrawMarker(lines, i);
                continue;
            }

            if (trim.startsWith("<<<PAPER_IMAGE:")) {
                String caption = trim.substring("<<<PAPER_IMAGE:".length());
                if (caption.endsWith(">>>")) {
                    caption = caption.substring(0, caption.length() - 3).trim();
                }
                i++;
                StringBuilder base64 = new StringBuilder();
                while (i < lines.length) {
                    String next = lines[i].strip();
                    if (next.equals("<<<END_PAPER_IMAGE>>>")) {
                        i++;
                        break;
                    }
                    if (next.startsWith("<<<PAPER_IMAGE:")) {
                        break;
                    }
                    if (next.isEmpty()) {
                        if (base64.length() > 0) {
                            i++;
                            break;
                        }
                        i++;
                        continue;
                    }
                    if (base64.length() > 0 && !next.matches("^[A-Za-z0-9+/=]+$")) {
                        break;
                    }
                    base64.append(next);
                    i++;
                }
                addEmbeddedImage(doc, base64.toString(), caption);
                continue;
            }

            if (trim.startsWith("```")) {
                inCode = !inCode;
                i++;
                continue;
            }
            if (inCode) {
                addCodeLine(doc, line);
                i++;
                continue;
            }
            if (trim.isEmpty()) {
                i++;
                continue;
            }
            // Markdown 表格块
            if (isTableLine(trim)) {
                int j = i;
                List<String> block = new ArrayList<>();
                while (j < lines.length && isTableLine(lines[j].strip())) {
                    block.add(lines[j].strip());
                    j++;
                }
                renderMarkdownTable(doc, block);
                i = j;
                continue;
            }
            // 图标题（图下方居中）/ 表标题（表上方居中）
            if (FIGURE_CAPTION.matcher(trim).matches() || TABLE_CAPTION.matcher(trim).matches()) {
                addCaption(doc, stripMarkdown(trim));
                i++;
                continue;
            }
            addBodyParagraph(doc, stripMarkdown(trim));
            i++;
        }
    }

    // ---------------- 段落 / 标题 / 表格 ----------------

    private void addHeading(XWPFDocument doc, String text, int level) {
        int safeLevel = Math.max(1, Math.min(level, 5));
        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        PaperFormatConfig.Heading heading = fmt().getHeading();
        XWPFParagraph p = doc.createParagraph();
        if (templateStyles != null) {
            p.setStyle(templateStyles.headingStyleId(safeLevel));
        } else {
            String style = switch (safeLevel) {
                case 1 -> STYLE_HEADING1;
                case 2 -> STYLE_HEADING2;
                default -> STYLE_HEADING3;
            };
            p.setStyle(style);
            applyOutlineLevel(p, Math.min(safeLevel - 1, 4));
        }
        // 禁止继承正文首行缩进，标题必须顶格
        clearParagraphIndent(p);
        if (safeLevel <= 1) {
            p.setAlignment(resolveAlign(heading.getH1Align(), ParagraphAlignment.CENTER));
            p.setSpacingBefore(ptToTwips(firstNonNull(heading.getH1SpacingBeforePt(), HEADING_SPACING_PT)));
            p.setSpacingAfter(ptToTwips(firstNonNull(heading.getH1SpacingAfterPt(), HEADING_SPACING_PT)));
        } else if (safeLevel == 2) {
            p.setAlignment(resolveAlign(heading.getH2Align(), ParagraphAlignment.LEFT));
            p.setSpacingBefore(ptToTwips(firstNonNull(heading.getH2SpacingBeforePt(), HEADING_SPACING_PT)));
            p.setSpacingAfter(ptToTwips(firstNonNull(heading.getH2SpacingAfterPt(), HEADING_SPACING_PT)));
        } else {
            p.setAlignment(resolveAlign(heading.getH3Align(), ParagraphAlignment.LEFT));
            p.setSpacingBefore(ptToTwips(firstNonNull(heading.getH3SpacingBeforePt(), HEADING_SPACING_PT)));
            p.setSpacingAfter(ptToTwips(firstNonNull(heading.getH3SpacingAfterPt(), HEADING_SPACING_PT)));
        }

        boolean bold = switch (safeLevel) {
            case 1 -> Boolean.TRUE.equals(heading.getH1Bold());
            case 2 -> Boolean.TRUE.equals(heading.getH2Bold());
            default -> Boolean.TRUE.equals(heading.getH3Bold());
        };
        XWPFRun run = p.createRun();
        run.setBold(bold);
        applyFont(run, fontHeadingAt(safeLevel), fontSizeHeadingAt(safeLevel));
        run.setText(text);
    }

    /** 摘要、参考文献等：视觉同一级标题，但不写入 Word 目录大纲 */
    private void addSectionTitle(XWPFDocument doc, String text) {
        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        PaperFormatConfig.Heading heading = fmt().getHeading();
        XWPFParagraph p = doc.createParagraph();
        if (templateStyles != null) {
            p.setStyle(templateStyles.getHeading1());
        } else {
            p.setStyle(STYLE_SECTION_TITLE);
        }
        clearParagraphIndent(p);
        p.setAlignment(resolveAlign(heading.getH1Align(), ParagraphAlignment.CENTER));
        p.setSpacingBefore(ptToTwips(firstNonNull(heading.getH1SpacingBeforePt(), HEADING_SPACING_PT)));
        p.setSpacingAfter(ptToTwips(firstNonNull(heading.getH1SpacingAfterPt(), HEADING_SPACING_PT)));
        XWPFRun run = p.createRun();
        run.setBold(Boolean.TRUE.equals(heading.getH1Bold()));
        applyFont(run, fontHeadingAt(1), fontSizeH1());
        run.setText(text);
    }

    private void applyOutlineLevel(XWPFParagraph paragraph, int outlineLevel) {
        CTPPr pPr = paragraph.getCTP().isSetPPr()
            ? paragraph.getCTP().getPPr()
            : paragraph.getCTP().addNewPPr();
        if (pPr.isSetOutlineLvl()) {
            pPr.getOutlineLvl().setVal(BigInteger.valueOf(outlineLevel));
        } else {
            pPr.addNewOutlineLvl().setVal(BigInteger.valueOf(outlineLevel));
        }
    }

    /**
     * 正文段落：首行缩进 2 字符、1.5 倍行距、角标 [n] 上标。
     */
    private void addBodyParagraph(XWPFDocument doc, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        text = stripLeadingParagraphIndent(text);
        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        XWPFParagraph p = doc.createParagraph();
        if (templateStyles != null && StringUtils.isNotBlank(templateStyles.getNormal())) {
            p.setStyle(templateStyles.getNormal());
        }
        applyBodyParagraphLayout(p);

        Matcher m = CITATION.matcher(text);
        int last = 0;
        while (m.find()) {
            // 行首 [n] 视为参考文献列表编号，不作上标角标
            int start = m.start();
            boolean atLineStart = start == 0 || text.charAt(start - 1) == '\n';
            if (m.start() > last) {
                addRun(p, text.substring(last, m.start()), false, templateStyles);
            }
            addRun(p, m.group(), !atLineStart, templateStyles);
            last = m.end();
        }
        if (last < text.length()) {
            addRun(p, text.substring(last), false, templateStyles);
        }
        applyBodyFirstLineIndent(p);
    }

    private void addRun(XWPFParagraph p, String text, boolean superscript, PaperTemplateStyleMapping templateStyles) {
        XWPFRun run = p.createRun();
        // 文献角标：右上标，小四号西文（默认 Times New Roman）
        if (superscript) {
            applyFont(run, fontTableAscii(), fontSizeH2());
            run.setVerticalAlignment("superscript");
        } else if (Boolean.TRUE.equals(acknowledgmentBody.get())) {
            applyFont(run, fontAcknowledgment(), fontSizeAcknowledgment());
        } else {
            applyFont(run, fontBody(), fontSizeBody());
        }
        run.setText(text);
    }

    /** 去掉段首空格/全角空格，避免与 Word 首行缩进叠加 */
    private static String stripLeadingParagraphIndent(String text) {
        if (text == null) {
            return "";
        }
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\u3000') {
                i++;
                continue;
            }
            break;
        }
        return i > 0 ? text.substring(i) : text;
    }

    private void addEmbeddedImage(XWPFDocument doc, String base64, String caption) {
        if (StringUtils.isBlank(base64)) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            addEmbeddedImageBytes(doc, bytes, caption, null);
        } catch (Exception e) {
            log.warn("嵌入论文图片失败: {}", e.getMessage());
            if (StringUtils.isNotBlank(caption)) {
                addCaption(doc, caption + "（图片加载失败）");
            }
        }
    }

    private void addEmbeddedImageBytes(XWPFDocument doc, byte[] bytes, String caption, String srcHint) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try {
            PictureType pictureType = detectPictureType(bytes, srcHint);
            String filename = pictureType == PictureType.SVG ? "figure.svg"
                : pictureType == PictureType.JPEG ? "figure.jpg" : "figure.png";
            byte[] embedBytes = pictureType == PictureType.PNG ? cropPngWhitespace(bytes) : bytes;
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            p.setSpacingBefore(80);
            p.setSpacingAfter(40);
            XWPFRun run = p.createRun();
            int[] sizePt = resolveEmbeddedImageSizePt(embedBytes, srcHint);
            try (ByteArrayInputStream in = new ByteArrayInputStream(embedBytes)) {
                run.addPicture(in, pictureType, filename, Units.toEMU(sizePt[0]), Units.toEMU(sizePt[1]));
            }
            if (StringUtils.isNotBlank(caption)) {
                addCaption(doc, caption);
            }
        } catch (Exception e) {
            log.warn("嵌入论文图片失败: {}", e.getMessage());
            if (StringUtils.isNotBlank(caption)) {
                addCaption(doc, caption + "（图片加载失败）");
            }
        }
    }

    private PictureType detectPictureType(byte[] bytes, String srcHint) {
        if (StringUtils.isNotBlank(srcHint) && srcHint.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            return PictureType.SVG;
        }
        if (bytes != null && bytes.length >= 4) {
            String head = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.UTF_8).trim();
            if (head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"))) {
                return PictureType.SVG;
            }
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
                return PictureType.JPEG;
            }
        }
        return PictureType.PNG;
    }

    private void addImageFromSrc(XWPFDocument doc, String src, String caption) {
        if (StringUtils.isBlank(src)) {
            return;
        }
        if (src.startsWith("data:image") && src.contains("/api/paper/assets/")) {
            src = src.substring(src.indexOf("/api/paper/assets/"));
        }
        if (src.startsWith("data:image/svg")) {
            int comma = src.indexOf(',');
            if (comma > 0 && comma < src.length() - 1) {
                String payload = src.substring(comma + 1);
                String decoded = java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8);
                byte[] bytes = decoded.trim().startsWith("<")
                    ? decoded.getBytes(StandardCharsets.UTF_8)
                    : Base64.getDecoder().decode(decoded.replaceAll("\\s", ""));
                addEmbeddedImageBytes(doc, bytes, caption, "figure.svg");
                return;
            }
        }
        if (src.startsWith("data:image")) {
            int comma = src.indexOf(',');
            if (comma > 0 && comma < src.length() - 1) {
                addEmbeddedImage(doc, src.substring(comma + 1), caption);
                return;
            }
        }
        if (src.contains("/api/paper/assets/")) {
            try {
                byte[] bytes = paperAssetService.readAssetBytes(src);
                addEmbeddedImageBytes(doc, bytes, caption, src);
            } catch (Exception e) {
                log.warn("读取本地论文图片失败: {} - {}", src, e.getMessage());
                if (StringUtils.isNotBlank(caption)) {
                    addCaption(doc, caption + "（图片读取失败）");
                }
            }
            return;
        }
        if (src.startsWith("http://") || src.startsWith("https://")) {
            try {
                byte[] bytes = java.net.URI.create(src).toURL().openStream().readAllBytes();
                addEmbeddedImageBytes(doc, bytes, caption, src);
            } catch (Exception e) {
                log.warn("下载论文图片失败: {} - {}", src, e.getMessage());
                if (StringUtils.isNotBlank(caption)) {
                    addCaption(doc, caption + "（图片下载失败）");
                }
            }
            return;
        }
        addEmbeddedImage(doc, src, caption);
    }

    /**
     * 按图片原始宽高比计算 Word 嵌入尺寸（pt）。
     * 在正文区宽×单页高范围内等比缩放（contain），避免流程图过高占满多页。
     */
    private int[] resolveEmbeddedImageSizePt(byte[] bytes, String srcHint) {
        int[] px = readImagePixelSize(bytes, srcHint);
        double widthPt = px[0] * 72.0 / 96.0;
        double heightPt = px[1] * 72.0 / 96.0;
        if (widthPt <= 0 || heightPt <= 0) {
            widthPt = contentMaxWidthPt();
            heightPt = contentMaxWidthPt() * 0.65;
        }
        double scale = Math.min(
            contentMaxWidthPt() / widthPt,
            CONTENT_MAX_HEIGHT_PT / heightPt
        );
        widthPt = widthPt * scale;
        heightPt = heightPt * scale;
        return new int[] {
            Math.max(120, (int) Math.round(widthPt)),
            Math.max(80, (int) Math.round(heightPt))
        };
    }

    private int[] readImagePixelSize(byte[] bytes, String srcHint) {
        if (bytes == null || bytes.length == 0) {
            return new int[] { 0, 0 };
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image != null) {
                return new int[] { image.getWidth(), image.getHeight() };
            }
        } catch (Exception e) {
            log.debug("读取位图尺寸失败: {}", e.getMessage());
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.UTF_8).trim();
        if (head.startsWith("<svg") || head.startsWith("<?xml") || head.contains("<svg")) {
            return parseSvgPixelSize(head);
        }
        if (StringUtils.isNotBlank(srcHint) && srcHint.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            String full = new String(bytes, StandardCharsets.UTF_8);
            return parseSvgPixelSize(full);
        }
        return new int[] { 0, 0 };
    }

    private int[] parseSvgPixelSize(String svg) {
        Matcher viewBoxMatcher = SVG_VIEWBOX.matcher(svg);
        if (viewBoxMatcher.find()) {
            String[] parts = viewBoxMatcher.group(1).trim().split("[\\s,]+");
            if (parts.length >= 4) {
                try {
                    double w = Double.parseDouble(parts[2]);
                    double h = Double.parseDouble(parts[3]);
                    if (w > 0 && h > 0) {
                        return new int[] { (int) Math.round(w), (int) Math.round(h) };
                    }
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        Matcher widthMatcher = SVG_WIDTH.matcher(svg);
        Matcher heightMatcher = SVG_HEIGHT.matcher(svg);
        if (widthMatcher.find() && heightMatcher.find()) {
            try {
                double w = Double.parseDouble(widthMatcher.group(1));
                double h = Double.parseDouble(heightMatcher.group(1));
                if (w > 0 && h > 0) {
                    return new int[] { (int) Math.round(w), (int) Math.round(h) };
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return new int[] { 0, 0 };
    }

    /** 裁掉 PNG 四周白边，避免 Word 按含大量留白的大图缩小导致流程图文字看不清 */
    private byte[] cropPngWhitespace(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return bytes;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int minX = width;
            int minY = height;
            int maxX = 0;
            int maxY = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    int a = (rgb >> 24) & 0xFF;
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    if (a < 20 || (r > 248 && g > 248 && b > 248)) {
                        continue;
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            if (maxX <= minX || maxY <= minY) {
                return bytes;
            }
            int padding = 10;
            minX = Math.max(0, minX - padding);
            minY = Math.max(0, minY - padding);
            maxX = Math.min(width - 1, maxX + padding);
            maxY = Math.min(height - 1, maxY + padding);
            int cropW = maxX - minX + 1;
            int cropH = maxY - minY + 1;
            BufferedImage cropped = image.getSubimage(minX, minY, cropW, cropH);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(cropped, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.debug("裁剪 PNG 白边失败: {}", e.getMessage());
            return bytes;
        }
    }

    private void addCaption(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(80);
        p.setSpacingAfter(80);
        XWPFRun run = p.createRun();
        // 图、表标题：小五号黑体
        applyFont(run, fontHeading(), fontSizeCaption());
        run.setText(text);
    }

    private void addCodeLine(XWPFDocument doc, String line) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        applyFont(run, fontCode(), 10);
        run.setText(line);
    }

    private void renderMarkdownTable(XWPFDocument doc, List<String> block) {
        List<String[]> rows = new ArrayList<>();
        for (String line : block) {
            if (isSeparatorLine(line)) {
                continue;
            }
            rows.add(splitRow(line));
        }
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.stream().mapToInt(r -> r.length).max().orElse(1);
        int rowCount = rows.size();
        boolean thesisGrid = isDbStructureTable(rows.get(0));
        int remarkCol = thesisGrid ? findRemarkColumnIndex(rows.get(0)) : -1;
        int[] colWidths = resolveTableColumnWidths(rows, cols, thesisGrid);
        XWPFTable table = doc.createTable(rowCount, cols);
        for (int r = 0; r < rowCount; r++) {
            String[] cells = rows.get(r);
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < cols; c++) {
                String val = c < cells.length ? cells[c] : "";
                XWPFTableCell cell = row.getCell(c);
                setCellText(cell, stripMarkdown(val), r == 0, thesisGrid && c == remarkCol, thesisGrid);
            }
        }
        // 文中表格均采用三线表；固定列宽避免 Word 自动挤窄短列
        finalizeThesisDbTable(table, rowCount, cols);
        applyFixedTableColumnWidths(table, colWidths);
        doc.createParagraph();
    }

    /** 数据库六列表用固定比例；其余按表头/内容估算，并保证表头汉字不纵向拆字 */
    private int[] resolveTableColumnWidths(List<String[]> rows, int cols, boolean thesisGrid) {
        if (thesisGrid && cols == DB_TABLE_COL_PCT.length) {
            return percentToTwips(DB_TABLE_COL_PCT, tableWidthTwips());
        }
        int[] weights = new int[cols];
        for (int c = 0; c < cols; c++) {
            int max = 2;
            for (String[] row : rows) {
                if (c < row.length && row[c] != null) {
                    max = Math.max(max, estimateDisplayWidth(row[c]));
                }
            }
            // 表头至少按汉字宽度留空，防止「长度」「主键」被挤成一字一换行
            weights[c] = Math.max(max, 4);
        }
        return weightToTwips(weights, tableWidthTwips());
    }

    /** 粗估显示宽度：中文≈2、英文/数字≈1 */
    private int estimateDisplayWidth(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            w += ch > 0x7F ? 2 : 1;
        }
        return Math.max(w, 1);
    }

    private int[] percentToTwips(int[] percents, int totalTwips) {
        int[] widths = new int[percents.length];
        int used = 0;
        for (int i = 0; i < percents.length; i++) {
            if (i == percents.length - 1) {
                widths[i] = Math.max(totalTwips - used, 1);
            } else {
                widths[i] = Math.max(totalTwips * percents[i] / 100, 1);
                used += widths[i];
            }
        }
        return widths;
    }

    private int[] weightToTwips(int[] weights, int totalTwips) {
        int sum = 0;
        for (int w : weights) {
            sum += w;
        }
        if (sum <= 0) {
            sum = weights.length;
            for (int i = 0; i < weights.length; i++) {
                weights[i] = 1;
            }
        }
        int[] widths = new int[weights.length];
        int used = 0;
        for (int i = 0; i < weights.length; i++) {
            if (i == weights.length - 1) {
                widths[i] = Math.max(totalTwips - used, 1);
            } else {
                widths[i] = Math.max(totalTwips * weights[i] / sum, 1);
                used += widths[i];
            }
        }
        return widths;
    }

    /** 固定表格总宽 + 列宽（tblGrid / tcW），禁止 Word autofit 挤窄短列 */
    private void applyFixedTableColumnWidths(XWPFTable table, int[] colWidths) {
        if (table == null || colWidths == null || colWidths.length == 0) {
            return;
        }
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();
        CTTblWidth tblW = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setType(STTblWidth.DXA);
        tblW.setW(BigInteger.valueOf(tableWidthTwips()));

        CTTblLayoutType layout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);

        CTTblGrid grid = ctTbl.getTblGrid() != null ? ctTbl.getTblGrid() : ctTbl.addNewTblGrid();
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int width : colWidths) {
            grid.addNewGridCol().setW(BigInteger.valueOf(width));
        }

        for (XWPFTableRow row : table.getRows()) {
            int cellCount = Math.min(row.getTableCells().size(), colWidths.length);
            for (int c = 0; c < cellCount; c++) {
                setCellWidth(row.getCell(c), colWidths[c]);
            }
        }
    }

    private void setCellWidth(XWPFTableCell cell, int widthTwips) {
        if (cell == null) {
            return;
        }
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        tcW.setType(STTblWidth.DXA);
        tcW.setW(BigInteger.valueOf(widthTwips));
    }

    /** 数据库表结构（字段名称/类型/长度/允许空值/主键/备注） */
    private boolean isDbStructureTable(String[] headerRow) {
        if (headerRow == null || headerRow.length < 4) {
            return false;
        }
        String joined = String.join("", headerRow);
        return joined.contains("字段名称") && joined.contains("主键");
    }

    private int findRemarkColumnIndex(String[] headerRow) {
        for (int i = 0; i < headerRow.length; i++) {
            String h = headerRow[i] == null ? "" : headerRow[i].strip();
            if (h.contains("备注") || h.contains("说明")) {
                return i;
            }
        }
        return headerRow.length - 1;
    }

    /** 清除模板默认表样式，并去掉 POI 写入单元格的实线边框（否则会覆盖表级虚线）。 */
    private void prepareThesisDbTable(XWPFTable table) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr();
        if (tblPr == null) {
            tblPr = ctTbl.addNewTblPr();
        }
        if (tblPr.isSetTblStyle()) {
            tblPr.unsetTblStyle();
        }
        if (tblPr.isSetTblLook()) {
            tblPr.unsetTblLook();
        }
        if (tblPr.isSetTblBorders()) {
            tblPr.unsetTblBorders();
        }
    }

    private void stripAllCellBorders(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                CTTc tc = cell.getCTTc();
                if (!tc.isSetTcPr()) {
                    continue;
                }
                CTTcPr tcPr = tc.getTcPr();
                if (tcPr.isSetTcBorders()) {
                    tcPr.unsetTcBorders();
                }
            }
        }
    }

    /**
     * 三线表：上下线 1.5 磅、表头下线 0.5 磅；无左右线与表内竖线。
     */
    private void finalizeThesisDbTable(XWPFTable table, int rowCount, int colCount) {
        prepareThesisDbTable(table);
        stripAllCellBorders(table);

        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }
        if (tblPr.isSetTblBorders()) {
            tblPr.unsetTblBorders();
        }
        CTTblBorders tblBorders = tblPr.addNewTblBorders();
        setBorder(tblBorders.addNewTop(), STBorder.SINGLE, THREE_LINE_OUTER, "000000");
        setBorder(tblBorders.addNewBottom(), STBorder.SINGLE, THREE_LINE_OUTER, "000000");
        setBorder(tblBorders.addNewLeft(), STBorder.NONE, 0, "000000");
        setBorder(tblBorders.addNewRight(), STBorder.NONE, 0, "000000");
        setBorder(tblBorders.addNewInsideH(), STBorder.NONE, 0, "000000");
        setBorder(tblBorders.addNewInsideV(), STBorder.NONE, 0, "000000");

        if (rowCount <= 0) {
            return;
        }
        XWPFTableRow headerRow = table.getRow(0);
        for (int c = 0; c < colCount; c++) {
            XWPFTableCell headerCell = headerRow.getCell(c);
            applyCellBorder(headerCell, "top", STBorder.SINGLE, THREE_LINE_OUTER, "000000");
            applyCellBorder(headerCell, "bottom", STBorder.SINGLE, THREE_LINE_INNER, "000000");
            applyCellBorder(headerCell, "left", STBorder.NONE, 0, "000000");
            applyCellBorder(headerCell, "right", STBorder.NONE, 0, "000000");
        }
        if (rowCount > 1) {
            XWPFTableRow lastRow = table.getRow(rowCount - 1);
            for (int c = 0; c < colCount; c++) {
                applyCellBorder(lastRow.getCell(c), "bottom", STBorder.SINGLE, THREE_LINE_OUTER, "000000");
                applyCellBorder(lastRow.getCell(c), "left", STBorder.NONE, 0, "000000");
                applyCellBorder(lastRow.getCell(c), "right", STBorder.NONE, 0, "000000");
            }
        }
    }

    private void setBorder(CTBorder border, STBorder.Enum style, int size, String color) {
        border.setVal(style);
        if (size > 0) {
            border.setSz(BigInteger.valueOf(size));
            border.setColor(color);
        }
    }

    private void applyCellBorder(
        XWPFTableCell cell,
        String side,
        STBorder.Enum style,
        int size,
        String color
    ) {
        if (cell == null) {
            return;
        }
        CTTc tc = cell.getCTTc();
        CTTcPr tcPr = tc.isSetTcPr() ? tc.getTcPr() : tc.addNewTcPr();
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        CTBorder border = switch (side) {
            case "top" -> borders.isSetTop() ? borders.getTop() : borders.addNewTop();
            case "bottom" -> borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom();
            case "left" -> borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft();
            case "right" -> borders.isSetRight() ? borders.getRight() : borders.addNewRight();
            default -> borders.addNewBottom();
        };
        border.setVal(style);
        if (size > 0) {
            border.setSz(BigInteger.valueOf(size));
            border.setColor(color);
        }
    }

    private void setCellText(XWPFTableCell cell, String text, boolean header) {
        setCellText(cell, text, header, false);
    }

    private void setCellText(XWPFTableCell cell, String text, boolean header, boolean remarkColumn) {
        setCellText(cell, text, header, remarkColumn, false);
    }

    private void setCellText(
        XWPFTableCell cell,
        String text,
        boolean header,
        boolean remarkColumn,
        boolean thesisTable
    ) {
        if (cell == null) {
            return;
        }
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        // 禁止继承正文「首行缩进 2 字符」，否则居中看起来偏右/不齐
        clearParagraphIndent(p);
        p.setAlignment(remarkColumn && !header ? ParagraphAlignment.LEFT : ParagraphAlignment.CENTER);
        p.setSpacingBetween(1.0, LineSpacingRule.AUTO);
        p.setSpacingBefore(0);
        p.setSpacingAfter(0);

        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTVerticalJc vJc = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
        vJc.setVal(STVerticalJc.CENTER);
        applyCellPadding(tcPr);

        XWPFRun run = p.createRun();
        // 表内：中文宋体五号，英文/数字 Times New Roman 五号
        applyFont(run, fontTableEastAsia(), fontSizeTable());
        run.setBold(false);
        run.setText(text == null ? "" : text);
    }

    /** 清除段落缩进（含字符单位），避免表内文字受正文样式影响 */
    private void clearParagraphIndent(XWPFParagraph p) {
        p.setIndentationFirstLine(0);
        p.setIndentationLeft(0);
        p.setIndentationRight(0);
        p.setIndentationHanging(0);
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        if (pPr.isSetInd()) {
            pPr.unsetInd();
        }
        CTInd ind = pPr.addNewInd();
        ind.setFirstLine(BigInteger.ZERO);
        ind.setFirstLineChars(BigInteger.ZERO);
        ind.setLeft(BigInteger.ZERO);
        ind.setRight(BigInteger.ZERO);
    }

    private void applyCellPadding(CTTcPr tcPr) {
        CTTcMar mar = tcPr.isSetTcMar() ? tcPr.getTcMar() : tcPr.addNewTcMar();
        setTblWidthDxa(mar.isSetLeft() ? mar.getLeft() : mar.addNewLeft(), TABLE_CELL_PAD_TWIPS);
        setTblWidthDxa(mar.isSetRight() ? mar.getRight() : mar.addNewRight(), TABLE_CELL_PAD_TWIPS);
        setTblWidthDxa(mar.isSetTop() ? mar.getTop() : mar.addNewTop(), TABLE_CELL_PAD_TWIPS);
        setTblWidthDxa(mar.isSetBottom() ? mar.getBottom() : mar.addNewBottom(), TABLE_CELL_PAD_TWIPS);
    }

    private void setTblWidthDxa(CTTblWidth width, int twips) {
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(twips));
    }

    // ---------------- 工具 ----------------

    private boolean isTableLine(String line) {
        if (!line.contains("|")) {
            return false;
        }
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '|') {
                count++;
            }
        }
        return count >= 2;
    }

    private boolean isSeparatorLine(String line) {
        String t = line.replace("|", "").replace(":", "").strip();
        return !t.isEmpty() && t.chars().allMatch(ch -> ch == '-' || ch == ' ');
    }

    private String[] splitRow(String line) {
        String s = line.strip();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|")) {
            s = s.substring(0, s.length() - 1);
        }
        String[] parts = s.split("\\|", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].strip();
        }
        return parts;
    }

    /**
     * 去除简单 Markdown 标记（标题井号、加粗星号等）。
     */
    private String stripMarkdown(String text) {
        String t = text;
        t = PAPER_DRAW_INLINE.matcher(t).replaceAll("");
        t = t.replaceAll("^#{1,6}\\s*", "");
        t = t.replace("**", "").replace("__", "");
        t = t.replaceAll("^[*\\-]\\s+", "• ");
        return t.trim();
    }

    /** 行是否为 paper-draw 元数据起始（含 XSS 剥残后的 <<>>） */
    private static boolean isPaperDrawMarkerStart(String trim) {
        if (trim.isEmpty()) {
            return false;
        }
        if ("<<>>".equals(trim)) {
            return true;
        }
        return trim.startsWith("[[[PAPER_DRAW:")
            || trim.startsWith("<<<PAPER_DRAW:")
            || (trim.startsWith("<!--") && trim.contains("paper-draw"));
    }

    /** 跳过单行或多行 paper-draw 标记，返回下一行下标 */
    private static int skipPaperDrawMarker(String[] lines, int start) {
        String first = lines[start].strip();
        if ("<<>>".equals(first)
            || (first.startsWith("[[[PAPER_DRAW:") && first.contains("]]]"))
            || (first.startsWith("<<<PAPER_DRAW:") && first.contains(">>>"))
            || (first.startsWith("<!--") && first.contains("-->"))) {
            return start + 1;
        }
        int i = start + 1;
        while (i < lines.length && i - start < 30) {
            String t = lines[i].strip();
            if (first.startsWith("[[[") && t.contains("]]]")) {
                return i + 1;
            }
            if (first.startsWith("<<<") && t.contains(">>>")) {
                return i + 1;
            }
            if (first.startsWith("<!--") && t.contains("-->")) {
                return i + 1;
            }
            i++;
        }
        return Math.min(start + 1, lines.length);
    }

    private void applyFont(XWPFRun run, String family, double size) {
        run.setFontFamily(family);
        run.setFontFamily(family, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        applyRunFontProperties(rPr, family, size);
    }

    /** 同时写入 w:sz 与 w:szCs（半磅），避免中文仍继承模板字号；并关闭 run 级拼写校对 */
    private void applyRunFontProperties(CTRPr rPr, String family, double sizePt) {
        BigInteger halfPoints = BigInteger.valueOf(Math.round(sizePt * 2));
        if (rPr.sizeOfSzArray() > 0) {
            rPr.getSzArray(0).setVal(halfPoints);
        } else {
            rPr.addNewSz().setVal(halfPoints);
        }
        if (rPr.sizeOfSzCsArray() > 0) {
            rPr.getSzCsArray(0).setVal(halfPoints);
        } else {
            rPr.addNewSzCs().setVal(halfPoints);
        }
        // 不对专有名词做拼写检查（配合文档 hideSpellingErrors）
        if (rPr.sizeOfNoProofArray() == 0) {
            rPr.addNewNoProof();
        }
        CTFonts fonts = rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();
        String ascii = resolveAsciiFont(family);
        fonts.setEastAsia(family);
        fonts.setAscii(ascii);
        fonts.setHAnsi(ascii);
        fonts.setCs(ascii);
    }

    /** 按东文字体族匹配有效配置中的西文配对；无法识别时原样使用 */
    private String resolveAsciiFont(String eastAsiaFamily) {
        if (eastAsiaFamily == null) {
            return FONT_TABLE;
        }
        if (eastAsiaFamily.equals(fontBody()) || eastAsiaFamily.equals(FONT_BODY)) {
            return fontBodyAscii();
        }
        if (eastAsiaFamily.equals(fontHeading()) || eastAsiaFamily.equals(FONT_HEADING)
            || eastAsiaFamily.equals(fontHeadingAt(1))
            || eastAsiaFamily.equals(fontHeadingAt(2))
            || eastAsiaFamily.equals(fontHeadingAt(3))
            || eastAsiaFamily.equals(fontHeadingAt(4))
            || eastAsiaFamily.equals(fontHeadingAt(5))) {
            return fontHeadingAscii();
        }
        if (eastAsiaFamily.equals(fontTableEastAsia())) {
            return fontTableAscii();
        }
        if (eastAsiaFamily.equals(fontFooter())) {
            return fontBodyAscii();
        }
        return eastAsiaFamily;
    }
}
