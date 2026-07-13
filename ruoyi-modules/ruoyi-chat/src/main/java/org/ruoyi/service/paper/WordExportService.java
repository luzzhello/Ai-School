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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabTlc;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperTemplateStyleMapping;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.domain.paper.TocNode;
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
 * 读取会话目录与已生成内容，按大连海洋大学本科毕业论文（设计）版式组装 docx：
 * A4；左30/右25/上30/下25mm；正文五号宋体、固定行距18磅；标题黑体不加粗（一/二/三级）；
 * 页眉「大连海洋大学本科毕业论文（设计）」+题目；页脚居中页码（前置罗马、正文阿拉伯）；三线表。
 * 对应 PRD「3.6 文档排版与导出模块」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordExportService {

    private static final String FONT_BODY = "宋体";
    private static final String FONT_HEADING = "黑体";
    private static final String FONT_CODE = "Consolas";
    /** 英文 / 数字 / 表内西文 */
    private static final String FONT_TABLE = "Times New Roman";

    /** 大连海洋大学本科毕业论文（设计）默认页眉左侧文案 */
    private static final String HEADER_SCHOOL = "大连海洋大学本科毕业论文（设计）";

    /**
     * 字号（pt）：三号 16 / 小四 12 / 五号 10.5 / 小五 9 / 小二 18。
     * 正文行距固定 18 磅（EXACT）。
     */
    private static final double FONT_SIZE_BODY = 10.5;       // 五号
    private static final double FONT_SIZE_TITLE_XIAO_ER = 18;  // 小二（摘要英文题目）
    private static final double FONT_SIZE_COVER_TITLE = 18;
    private static final double FONT_SIZE_H1 = 16;           // 三号
    private static final double FONT_SIZE_H2 = 12;           // 小四
    private static final double FONT_SIZE_H3 = 10.5;         // 五号
    private static final double FONT_SIZE_CAPTION = 9;       // 小五（图/表题）
    private static final double FONT_SIZE_HEADER = 9;        // 小五（页眉页脚）
    private static final double FONT_SIZE_FOOTER = 9;
    private static final double FONT_SIZE_TABLE = 10.5;      // 表内五号
    private static final int BODY_LINE_SPACING_TWIPS = 360;  // 18 磅
    /** 标题段前/段后约 1 行（twips） */
    private static final int HEADING_SPACING_LINE = 240;
    /** 正文首行缩进 2 字符（五号约 420 twips） */
    private static final int BODY_FIRST_LINE_CHARS = 200;
    private static final int BODY_FIRST_LINE_TWIPS = 420;

    /** A4 尺寸（twips）：210mm × 297mm */
    private static final int A4_WIDTH = 11906;
    private static final int A4_HEIGHT = 16838;
    /** 边距：左 30 / 右 25 / 上 30 / 下 25（mm→twips，1mm≈56.7） */
    private static final int MARGIN_LEFT = 1701;
    private static final int MARGIN_RIGHT = 1418;
    private static final int MARGIN_TOP = 1701;
    private static final int MARGIN_BOTTOM = 1418;
    /** 页眉边距 23mm、页脚边距 18mm */
    private static final int HEADER_DISTANCE = 1304;
    private static final int FOOTER_DISTANCE = 1021;
    /** 目录行右对齐制表位（正文区右边界） */
    private static final int TOC_TAB_POS = A4_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
    /** 正文区可用宽度（pt）：210−30−25=155mm */
    private static final int CONTENT_MAX_WIDTH_PT = 439;
    private static final int CONTENT_MAX_HEIGHT_PT = 620;
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
    /** 摘要中英文分界 */
    private static final Pattern ABSTRACT_HEADER = Pattern.compile("(?im)^Abstract:?\\s*$");
    private static final Pattern ABSTRACT_LINE = Pattern.compile("(?im)^ABSTRACT\\s*$");
    private static final Pattern CHINESE_KEYWORDS = Pattern.compile("(?m)^关键词[：:]");
    private static final Pattern ENGLISH_KEYWORDS = Pattern.compile("(?im)^Keywords\\s*:");

    private final PaperSessionStore paperSessionStore;
    private final PaperAssetService paperAssetService;
    private final PaperTemplateService paperTemplateService;
    private final PaperExportLayoutEstimator layoutEstimator = new PaperExportLayoutEstimator();

    /** 单次导出线程内使用的模板样式映射 */
    private final ThreadLocal<PaperTemplateStyleMapping> exportTemplateStyles = new ThreadLocal<>();

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
        try (InputStream templateIn = paperTemplateService.openTemplateInputStream();
             XWPFDocument doc = openCleanTemplateDocument(templateIn)) {
            PaperTemplateStyleMapping templateStyles = paperTemplateService.getStyleMapping();
            exportTemplateStyles.set(templateStyles);
            applyDalianOceanPageSetup(doc);
            applyHeaderAndFooter(doc, session.getTitle());
            patchTemplateStyles(doc);
            writeTitle(doc, session.getTitle(), templateStyles);
            writeBody(doc, session);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("导出论文 Word 失败, sessionId={}", sessionId, e);
            throw new ServiceException("导出 Word 失败");
        } finally {
            exportTemplateStyles.remove();
        }
    }

    private PaperTemplateStyleMapping currentTemplateStyles() {
        PaperTemplateStyleMapping styles = exportTemplateStyles.get();
        return styles != null ? styles : PaperTemplateStyleMapping.defaults();
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
            disableAutoFieldUpdate(raw);
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

    /** 禁止 Word 打开时自动更新域，避免残留 TOC 域被重新展开 */
    private void disableAutoFieldUpdate(XWPFDocument doc) {
        if (doc.getSettings() == null) {
            return;
        }
        var settings = doc.getSettings().getCTSettings();
        if (settings.isSetUpdateFields()) {
            settings.getUpdateFields().setVal(false);
        } else {
            settings.addNewUpdateFields().setVal(false);
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

    /** 大连海洋大学版式：页边距 + 页眉/页脚距离 */
    private void applyDalianOceanPageSetup(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
            ? doc.getDocument().getBody().getSectPr()
            : doc.getDocument().getBody().addNewSectPr();
        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSz.setW(BigInteger.valueOf(A4_WIDTH));
        pageSz.setH(BigInteger.valueOf(A4_HEIGHT));
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setLeft(BigInteger.valueOf(MARGIN_LEFT));
        mar.setRight(BigInteger.valueOf(MARGIN_RIGHT));
        mar.setTop(BigInteger.valueOf(MARGIN_TOP));
        mar.setBottom(BigInteger.valueOf(MARGIN_BOTTOM));
        mar.setHeader(BigInteger.valueOf(HEADER_DISTANCE));
        mar.setFooter(BigInteger.valueOf(FOOTER_DISTANCE));
        // 前置部分（摘要/目录）用罗马数字页码，正文分节后改为阿拉伯数字
        CTPageNumber pgNum = sectPr.isSetPgNumType() ? sectPr.getPgNumType() : sectPr.addNewPgNumType();
        pgNum.setFmt(STNumberFormat.UPPER_ROMAN);
        pgNum.setStart(BigInteger.ONE);
    }

    /** 页眉：左学校名、右论文题目；页脚居中页码（小五宋体） */
    private void applyHeaderAndFooter(XWPFDocument doc, String paperTitle) {
        resetHeaderFooters(doc);
        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph headerPara = header.createParagraph();
        headerPara.setAlignment(ParagraphAlignment.LEFT);
        CTPPr pPr = headerPara.getCTP().isSetPPr() ? headerPara.getCTP().getPPr() : headerPara.getCTP().addNewPPr();
        if (pPr.isSetTabs()) {
            pPr.unsetTabs();
        }
        CTTabs tabs = pPr.addNewTabs();
        CTTabStop rightTab = tabs.addNewTab();
        rightTab.setVal(STTabJc.RIGHT);
        rightTab.setPos(BigInteger.valueOf(TOC_TAB_POS));

        XWPFRun left = headerPara.createRun();
        applyFont(left, FONT_BODY, FONT_SIZE_HEADER);
        left.setText(HEADER_SCHOOL);
        XWPFRun tabRun = headerPara.createRun();
        tabRun.addTab();
        XWPFRun right = headerPara.createRun();
        applyFont(right, FONT_BODY, FONT_SIZE_HEADER);
        right.setText(StringUtils.isBlank(paperTitle) ? "" : paperTitle.trim());

        ensureFooterPageNumber(doc, HeaderFooterType.DEFAULT, false);
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
            ? doc.getDocument().getBody().getSectPr()
            : null;
        if (sectPr != null && sectPr.isSetTitlePg()) {
            ensureFooterPageNumber(doc, HeaderFooterType.FIRST, false);
        }
    }

    private void ensureFooterPageNumber(XWPFDocument doc, HeaderFooterType type, boolean arabic) {
        XWPFFooter footer = doc.createFooter(type);
        XWPFParagraph para = footer.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        appendPageNumberField(para, arabic);
    }

    private void appendPageNumberField(XWPFParagraph paragraph, boolean arabic) {
        XWPFRun run = paragraph.createRun();
        applyFont(run, FONT_BODY, FONT_SIZE_FOOTER);
        CTR ctr = run.getCTR();

        CTFldChar begin = ctr.addNewFldChar();
        begin.setFldCharType(STFldCharType.BEGIN);

        CTText instr = ctr.addNewInstrText();
        // 节属性决定罗马/阿拉伯；域本身用 PAGE
        instr.setStringValue(" PAGE ");

        CTFldChar separate = ctr.addNewFldChar();
        separate.setFldCharType(STFldCharType.SEPARATE);

        ctr.addNewT().setStringValue(arabic ? "1" : "I");

        CTFldChar end = ctr.addNewFldChar();
        end.setFldCharType(STFldCharType.END);
    }

    /**
     * 覆盖模板 styles.xml 中的字号/字体（模板 Normal 多为五号，中文实际读 szCs）。
     * 仅作用于本次导出的 docx 副本，不写回磁盘模板。
     */
    private void patchTemplateStyles(XWPFDocument doc) {
        PaperTemplateStyleMapping mapping = currentTemplateStyles();
        if (mapping == null) {
            return;
        }
        patchStyleFont(doc, mapping.getNormal(), FONT_BODY, FONT_SIZE_BODY, false);
        patchStyleFirstLineIndent(doc, mapping.getNormal());
        patchStyleFont(doc, mapping.getReference(), FONT_BODY, FONT_SIZE_BODY, false);
        // 规范：标题黑体不加粗
        patchStyleFont(doc, mapping.getHeading1(), FONT_HEADING, FONT_SIZE_H1, false);
        patchStyleFont(doc, mapping.getHeading2(), FONT_HEADING, FONT_SIZE_H2, false);
        patchStyleFont(doc, mapping.getHeading3(), FONT_HEADING, FONT_SIZE_H3, false);
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
        tab.setPos(BigInteger.valueOf(TOC_TAB_POS));
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
        ind.setFirstLineChars(BigInteger.valueOf(BODY_FIRST_LINE_CHARS));
        ind.setFirstLine(BigInteger.valueOf(BODY_FIRST_LINE_TWIPS));
    }

    private void applyBodyFirstLineIndent(XWPFParagraph paragraph) {
        CTPPr pPr = paragraph.getCTP().isSetPPr()
            ? paragraph.getCTP().getPPr()
            : paragraph.getCTP().addNewPPr();
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setFirstLineChars(BigInteger.valueOf(BODY_FIRST_LINE_CHARS));
        ind.setFirstLine(BigInteger.valueOf(BODY_FIRST_LINE_TWIPS));
    }

    private void applyBodyParagraphLayout(XWPFParagraph paragraph) {
        // 正文：五号宋体 + 固定行距 18 磅，段前段后 0
        paragraph.setSpacingBetween(BODY_LINE_SPACING_TWIPS / 20.0, LineSpacingRule.EXACT);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setAlignment(ParagraphAlignment.BOTH);
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

    private void applyPageSetup(XWPFDocument doc) {
        applyDalianOceanPageSetup(doc);
    }

    /** 注册 Heading1-3 样式（含 outlineLvl），使 Word 左侧导航与自动目录可识别章节结构 */
    private void ensureDocumentStyles(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles();
        addHeadingStyle(styles, STYLE_HEADING1, "heading 1", 0, FONT_SIZE_H1);
        addHeadingStyle(styles, STYLE_HEADING2, "heading 2", 1, FONT_SIZE_H2);
        addHeadingStyle(styles, STYLE_HEADING3, "heading 3", 2, FONT_SIZE_H3);
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
        // 规范：标题不加粗
        applyRunFontProperties(rPr, FONT_HEADING, fontSize);

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
        applyRunFontProperties(rPr, FONT_HEADING, FONT_SIZE_H1);

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
        run.setBold(true);
        applyFont(run, FONT_HEADING, FONT_SIZE_TITLE_XIAO_ER);
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
            applyFont(run, FONT_HEADING, FONT_SIZE_BODY);
        } else {
            applyFont(run, FONT_TABLE, FONT_SIZE_BODY);
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
        applyFont(run, FONT_TABLE, FONT_SIZE_TITLE_XIAO_ER);
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
        applyFont(run, chinese ? FONT_BODY : FONT_TABLE, FONT_SIZE_BODY);
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
            applyFont(run, chinese ? FONT_BODY : FONT_TABLE, FONT_SIZE_BODY);
            run.setText(line);
            return;
        }
        String label = line.substring(0, sep);
        String rest = line.substring(sep);
        XWPFRun labelRun = p.createRun();
        labelRun.setBold(true);
        applyFont(labelRun, chinese ? FONT_BODY : FONT_TABLE, FONT_SIZE_BODY);
        labelRun.setText(label);
        XWPFRun restRun = p.createRun();
        applyFont(restRun, chinese ? FONT_BODY : FONT_TABLE, FONT_SIZE_BODY);
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
        for (TocNode node : toc) {
            if (isTocPageNode(node)) {
                continue;
            }
            if (!tocPageInserted && !isAbstractNode(node)) {
                writeTableOfContentsPage(doc, toc, headingPages);
                tocPageInserted = true;
            }
            writeNode(doc, node, session);
            if (!tocPageInserted && isAbstractNode(node)) {
                writeTableOfContentsPage(doc, toc, headingPages);
                tocPageInserted = true;
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
        } else {
            if (isReferenceNode(node)) {
                addSectionTitle(doc, title);
            } else {
                addHeading(doc, title, level);
            }
            if (StringUtils.isBlank(content) && isReferenceNode(node)) {
                renderReferences(doc, session.getReferences());
            } else if (StringUtils.isNotBlank(content)) {
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
     * 摘要之后插入目录页：居中标题 + 静态目录（点线引导符 + 估算页码，无需更新域）。
     */
    private void writeTableOfContentsPage(XWPFDocument doc, List<TocNode> toc, Map<String, Integer> headingPages) {
        addPageBreak(doc);

        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        titlePara.setSpacingAfter(200);
        XWPFRun titleRun = titlePara.createRun();
        applyFont(titleRun, FONT_HEADING, FONT_SIZE_H1);
        titleRun.setBold(false);
        titleRun.setText("目录");

        for (TocNode node : toc) {
            appendStaticTocEntry(doc, node, headingPages);
        }

        // 目录之后进入正文：新节 + 阿拉伯数字页码从 1 起
        addBodySectionBreak(doc);
    }

    /** 正文分节：页码改为阿拉伯数字并从 1 重新编号 */
    private void addBodySectionBreak(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTSectPr sectPr = pPr.isSetSectPr() ? pPr.getSectPr() : pPr.addNewSectPr();
        if (sectPr.isSetType()) {
            sectPr.getType().setVal(STSectionMark.NEXT_PAGE);
        } else {
            sectPr.addNewType().setVal(STSectionMark.NEXT_PAGE);
        }
        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSz.setW(BigInteger.valueOf(A4_WIDTH));
        pageSz.setH(BigInteger.valueOf(A4_HEIGHT));
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setLeft(BigInteger.valueOf(MARGIN_LEFT));
        mar.setRight(BigInteger.valueOf(MARGIN_RIGHT));
        mar.setTop(BigInteger.valueOf(MARGIN_TOP));
        mar.setBottom(BigInteger.valueOf(MARGIN_BOTTOM));
        mar.setHeader(BigInteger.valueOf(HEADER_DISTANCE));
        mar.setFooter(BigInteger.valueOf(FOOTER_DISTANCE));
        CTPageNumber pgNum = sectPr.isSetPgNumType() ? sectPr.getPgNumType() : sectPr.addNewPgNumType();
        pgNum.setFmt(STNumberFormat.DECIMAL);
        pgNum.setStart(BigInteger.ONE);
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
            applyFont(run, FONT_BODY, FONT_SIZE_BODY);
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
        tab.setPos(BigInteger.valueOf(TOC_TAB_POS));
    }

    private void addPageBreak(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.addBreak(BreakType.PAGE);
    }

    /**
     * 渲染参考文献列表（GB/T 7714，悬挂式编号）。
     */
    private void renderReferences(XWPFDocument doc, List<Reference> references) {
        if (references == null || references.isEmpty()) {
            return;
        }
        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        for (Reference ref : references) {
            XWPFParagraph p = doc.createParagraph();
            if (templateStyles != null && StringUtils.isNotBlank(templateStyles.getReference())) {
                p.setStyle(templateStyles.getReference());
            } else {
                p.setSpacingBetween(1.5, LineSpacingRule.AUTO);
            }
            XWPFRun run = p.createRun();
            applyFont(run, FONT_BODY, FONT_SIZE_BODY);
            String text = "[" + (ref.getIndex() == null ? "" : ref.getIndex()) + "] "
                + (StringUtils.isNotBlank(ref.getCitation()) ? ref.getCitation() : "");
            run.setText(text);
        }
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
        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        XWPFParagraph p = doc.createParagraph();
        if (templateStyles != null) {
            p.setStyle(templateStyles.headingStyleId(level));
        } else {
            String style = switch (level) {
                case 1 -> STYLE_HEADING1;
                case 2 -> STYLE_HEADING2;
                default -> STYLE_HEADING3;
            };
            p.setStyle(style);
            applyOutlineLevel(p, Math.min(level - 1, 2));
        }
        // 一级居中；二/三级顶格；段前段后约 1 行；不加粗
        if (level <= 1) {
            p.setAlignment(ParagraphAlignment.CENTER);
        } else {
            p.setAlignment(ParagraphAlignment.LEFT);
        }
        p.setSpacingBefore(HEADING_SPACING_LINE);
        p.setSpacingAfter(HEADING_SPACING_LINE);

        double size = switch (level) {
            case 1 -> FONT_SIZE_H1;
            case 2 -> FONT_SIZE_H2;
            default -> FONT_SIZE_H3;
        };
        XWPFRun run = p.createRun();
        run.setBold(false);
        applyFont(run, FONT_HEADING, size);
        run.setText(text);
    }

    /** 摘要、参考文献等：视觉同一级标题，但不写入 Word 目录大纲 */
    private void addSectionTitle(XWPFDocument doc, String text) {
        PaperTemplateStyleMapping templateStyles = currentTemplateStyles();
        XWPFParagraph p = doc.createParagraph();
        if (templateStyles != null) {
            p.setStyle(templateStyles.getHeading1());
        } else {
            p.setStyle(STYLE_SECTION_TITLE);
        }
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(HEADING_SPACING_LINE);
        p.setSpacingAfter(HEADING_SPACING_LINE);
        XWPFRun run = p.createRun();
        run.setBold(false);
        applyFont(run, FONT_HEADING, FONT_SIZE_H1);
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
        // 文献角标：右上标，小四号 Times New Roman（校方规范）
        if (superscript) {
            applyFont(run, FONT_TABLE, FONT_SIZE_H2);
            run.setVerticalAlignment("superscript");
        } else {
            applyFont(run, FONT_BODY, FONT_SIZE_BODY);
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
            widthPt = CONTENT_MAX_WIDTH_PT;
            heightPt = CONTENT_MAX_WIDTH_PT * 0.65;
        }
        double scale = Math.min(
            CONTENT_MAX_WIDTH_PT / widthPt,
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
        applyFont(run, FONT_HEADING, FONT_SIZE_CAPTION);
        run.setText(text);
    }

    private void addCodeLine(XWPFDocument doc, String line) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        applyFont(run, FONT_CODE, 10);
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
        XWPFTable table = doc.createTable(rowCount, cols);
        table.setWidth("100%");
        for (int r = 0; r < rowCount; r++) {
            String[] cells = rows.get(r);
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < cols; c++) {
                String val = c < cells.length ? cells[c] : "";
                XWPFTableCell cell = row.getCell(c);
                setCellText(cell, stripMarkdown(val), r == 0, thesisGrid && c == remarkCol, thesisGrid);
            }
        }
        // 文中表格均采用三线表
        finalizeThesisDbTable(table, rowCount, cols);
        doc.createParagraph();
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
        p.setAlignment(remarkColumn && !header ? ParagraphAlignment.LEFT : ParagraphAlignment.CENTER);
        p.setSpacingBetween(1.0, LineSpacingRule.AUTO);
        p.setSpacingBefore(0);
        p.setSpacingAfter(0);
        XWPFRun run = p.createRun();
        // 表内：中文宋体五号，英文/数字 Times New Roman 五号
        applyFont(run, FONT_BODY, FONT_SIZE_TABLE);
        run.setBold(false);
        run.setText(text);
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
        t = t.replaceAll("^#{1,6}\\s*", "");
        t = t.replace("**", "").replace("__", "");
        t = t.replaceAll("^[*\\-]\\s+", "• ");
        return t;
    }

    private void applyFont(XWPFRun run, String family, double size) {
        run.setFontFamily(family);
        run.setFontFamily(family, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        applyRunFontProperties(rPr, family, size);
    }

    /** 同时写入 w:sz 与 w:szCs（半磅），避免中文仍继承模板字号 */
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
        CTFonts fonts = rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();
        fonts.setEastAsia(family);
        fonts.setAscii(family.equals(FONT_BODY) ? FONT_TABLE : family);
        fonts.setHAnsi(family.equals(FONT_BODY) ? FONT_TABLE : family);
        fonts.setCs(family.equals(FONT_BODY) ? FONT_TABLE : family);
    }
}
