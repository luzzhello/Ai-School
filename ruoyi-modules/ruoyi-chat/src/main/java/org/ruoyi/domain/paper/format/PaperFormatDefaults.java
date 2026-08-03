package org.ruoyi.domain.paper.format;

/**
 * 内置默认排版配置（大连海洋大学，与 {@code WordExportService} 硬编码一致）。
 */
public final class PaperFormatDefaults {

    private PaperFormatDefaults() {
    }

    public static PaperFormatConfig dalianOcean() {
        PaperFormatConfig config = new PaperFormatConfig();

        PaperFormatConfig.Page page = config.getPage();
        page.setPaper("A4");
        page.setMarginTopMm(30.0);
        page.setMarginBottomMm(25.0);
        page.setMarginLeftMm(30.0);
        page.setMarginRightMm(25.0);

        PaperFormatConfig.Font font = config.getFont();
        font.setBodyEastAsia("宋体");
        font.setBodyAscii("Times New Roman");
        font.setHeadingEastAsia("黑体");
        font.setHeadingAscii("Times New Roman");
        font.setTableEastAsia("宋体");
        font.setTableAscii("Times New Roman");
        font.setCode("Consolas");
        font.setFooterEastAsia("宋体");

        PaperFormatConfig.FontSize fontSize = config.getFontSize();
        fontSize.setTitle(18.0);
        fontSize.setHeading1(16.0);
        fontSize.setHeading2(12.0);
        fontSize.setHeading3(10.5);
        fontSize.setHeading4(10.5);
        fontSize.setHeading5(10.5);
        fontSize.setBody(10.5);
        fontSize.setCaption(9.0);
        fontSize.setFooter(9.0);
        fontSize.setToc(10.5);
        fontSize.setReference(10.5);
        fontSize.setAbstractLabel(10.5);
        fontSize.setAbstractBody(10.5);
        fontSize.setKeyword(10.5);
        fontSize.setAcknowledgment(10.5);

        PaperFormatConfig.Paragraph paragraph = config.getParagraph();
        paragraph.setLineSpacingPt(18.0);
        paragraph.setLineSpacingRule("exact");
        paragraph.setLineSpacingMultiple(1.5);
        paragraph.setFirstLineIndentChars(2);
        paragraph.setBodyAlign("both");

        PaperFormatConfig.Heading heading = config.getHeading();
        heading.setH1Align("center");
        heading.setH2Align("left");
        heading.setH3Align("left");
        heading.setH1Bold(false);
        heading.setH2Bold(false);
        heading.setH3Bold(false);
        heading.setTitleBold(true);
        heading.setH1SpacingBeforePt(12.0);
        heading.setH1SpacingAfterPt(12.0);
        heading.setH2SpacingBeforePt(12.0);
        heading.setH2SpacingAfterPt(12.0);
        heading.setH3SpacingBeforePt(12.0);
        heading.setH3SpacingAfterPt(12.0);

        PaperFormatConfig.HeaderFooter headerFooter = config.getHeaderFooter();
        headerFooter.setOddHeader("");
        headerFooter.setEvenHeader("");
        headerFooter.setFooterFormat("numeric");

        PaperFormatConfig.Export export = config.getExport();
        export.setPatchTemplateStyles(true);
        export.setApplyPageSetup(true);

        return config;
    }
}
