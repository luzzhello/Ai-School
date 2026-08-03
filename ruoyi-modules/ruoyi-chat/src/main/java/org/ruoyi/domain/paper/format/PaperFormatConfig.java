package org.ruoyi.domain.paper.format;

import lombok.Data;

/**
 * 论文 Word 排版配置（与前端 format JSON 同构，字段可稀疏覆盖）。
 */
@Data
public class PaperFormatConfig {

    private Page page;
    private Font font;
    private FontSize fontSize;
    private Paragraph paragraph;
    private Heading heading;
    private HeaderFooter headerFooter;
    private Export export;

    public Page getPage() {
        if (page == null) {
            page = new Page();
        }
        return page;
    }

    public Font getFont() {
        if (font == null) {
            font = new Font();
        }
        return font;
    }

    public FontSize getFontSize() {
        if (fontSize == null) {
            fontSize = new FontSize();
        }
        return fontSize;
    }

    public Paragraph getParagraph() {
        if (paragraph == null) {
            paragraph = new Paragraph();
        }
        return paragraph;
    }

    public Heading getHeading() {
        if (heading == null) {
            heading = new Heading();
        }
        return heading;
    }

    public HeaderFooter getHeaderFooter() {
        if (headerFooter == null) {
            headerFooter = new HeaderFooter();
        }
        return headerFooter;
    }

    public Export getExport() {
        if (export == null) {
            export = new Export();
        }
        return export;
    }

    /** Direct field access without lazy initialization (merge / validate / JSON). */
    public Page nestedPage() {
        return page;
    }

    public Font nestedFont() {
        return font;
    }

    public FontSize nestedFontSize() {
        return fontSize;
    }

    public Paragraph nestedParagraph() {
        return paragraph;
    }

    public Heading nestedHeading() {
        return heading;
    }

    public HeaderFooter nestedHeaderFooter() {
        return headerFooter;
    }

    public Export nestedExport() {
        return export;
    }

    @Data
    public static class Page {
        /** 纸张规格，一期仅 A4 */
        private String paper;
        private Double marginTopMm;
        private Double marginBottomMm;
        private Double marginLeftMm;
        private Double marginRightMm;
    }

    @Data
    public static class Font {
        private String bodyEastAsia;
        private String bodyAscii;
        private String headingEastAsia;
        private String headingAscii;
        /** 分级别标题字体（空则回退 headingEastAsia） */
        private String heading1EastAsia;
        private String heading2EastAsia;
        private String heading3EastAsia;
        private String heading4EastAsia;
        private String heading5EastAsia;
        private String tableEastAsia;
        private String tableAscii;
        private String code;
        private String footerEastAsia;
        private String abstractEastAsia;
        private String keywordEastAsia;
        private String referenceEastAsia;
        private String acknowledgmentEastAsia;
    }

    @Data
    public static class FontSize {
        private Double title;
        private Double heading1;
        private Double heading2;
        private Double heading3;
        private Double heading4;
        private Double heading5;
        private Double body;
        private Double abstractLabel;
        private Double abstractBody;
        private Double keyword;
        private Double caption;
        private Double reference;
        private Double acknowledgment;
        private Double footer;
        private Double toc;
    }

    @Data
    public static class Paragraph {
        /** 固定行距（磅） */
        private Double lineSpacingPt;
        /** exact / auto */
        private String lineSpacingRule;
        /** 倍行距，仅 auto 时生效 */
        private Double lineSpacingMultiple;
        /** 首行缩进（字符） */
        private Integer firstLineIndentChars;
        /** both / left / center / right */
        private String bodyAlign;
    }

    @Data
    public static class Heading {
        private String h1Align;
        private String h2Align;
        private String h3Align;
        private Boolean h1Bold;
        private Boolean h2Bold;
        private Boolean h3Bold;
        private Boolean titleBold;
        private Double h1SpacingBeforePt;
        private Double h1SpacingAfterPt;
        private Double h2SpacingBeforePt;
        private Double h2SpacingAfterPt;
        private Double h3SpacingBeforePt;
        private Double h3SpacingAfterPt;
    }

    @Data
    public static class HeaderFooter {
        /** 奇数页页眉 */
        private String oddHeader;
        /** 偶数页页眉 */
        private String evenHeader;
        /** 页脚格式：numeric / roman / none 等展示文案由前端约定 */
        private String footerFormat;
    }

    @Data
    public static class Export {
        private Boolean patchTemplateStyles;
        private Boolean applyPageSetup;
    }
}
