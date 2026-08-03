package org.ruoyi.service.paper;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.format.PaperFormatConfig;

/**
 * 论文排版配置合并与校验。
 */
public final class PaperFormatMerger {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE);

    private static final double MARGIN_MIN_MM = 5.0;
    private static final double MARGIN_MAX_MM = 50.0;
    private static final double FONT_SIZE_MIN_PT = 6.0;
    private static final double FONT_SIZE_MAX_PT = 72.0;
    private static final double LINE_SPACING_MIN_PT = 10.0;
    private static final double LINE_SPACING_MAX_PT = 40.0;
    private static final double LINE_SPACING_MULTIPLE_MIN = 1.0;
    private static final double LINE_SPACING_MULTIPLE_MAX = 3.0;

    private PaperFormatMerger() {
    }

    /** Later layers win; null fields in overlay are skipped. */
    public static PaperFormatConfig merge(PaperFormatConfig... layers) {
        PaperFormatConfig result = new PaperFormatConfig();
        if (layers == null) {
            return result;
        }
        for (PaperFormatConfig layer : layers) {
            if (layer == null) {
                continue;
            }
            overlay(result, layer);
        }
        return result;
    }

    public static void validate(PaperFormatConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("PaperFormatConfig must not be null");
        }

        PaperFormatConfig.Page page = config.nestedPage();
        if (page != null) {
            validateMargin("page.marginTopMm", page.getMarginTopMm());
            validateMargin("page.marginBottomMm", page.getMarginBottomMm());
            validateMargin("page.marginLeftMm", page.getMarginLeftMm());
            validateMargin("page.marginRightMm", page.getMarginRightMm());
        }

        PaperFormatConfig.FontSize fontSize = config.nestedFontSize();
        if (fontSize != null) {
            validateFontSize("fontSize.title", fontSize.getTitle());
            validateFontSize("fontSize.heading1", fontSize.getHeading1());
            validateFontSize("fontSize.heading2", fontSize.getHeading2());
            validateFontSize("fontSize.heading3", fontSize.getHeading3());
            validateFontSize("fontSize.heading4", fontSize.getHeading4());
            validateFontSize("fontSize.heading5", fontSize.getHeading5());
            validateFontSize("fontSize.body", fontSize.getBody());
            validateFontSize("fontSize.abstractLabel", fontSize.getAbstractLabel());
            validateFontSize("fontSize.abstractBody", fontSize.getAbstractBody());
            validateFontSize("fontSize.keyword", fontSize.getKeyword());
            validateFontSize("fontSize.caption", fontSize.getCaption());
            validateFontSize("fontSize.reference", fontSize.getReference());
            validateFontSize("fontSize.acknowledgment", fontSize.getAcknowledgment());
            validateFontSize("fontSize.footer", fontSize.getFooter());
            validateFontSize("fontSize.toc", fontSize.getToc());
        }

        PaperFormatConfig.Paragraph paragraph = config.nestedParagraph();
        if (paragraph != null) {
            validateLineSpacingPt("paragraph.lineSpacingPt", paragraph.getLineSpacingPt());
            validateLineSpacingMultiple("paragraph.lineSpacingMultiple", paragraph.getLineSpacingMultiple());
        }

        PaperFormatConfig.Font font = config.nestedFont();
        if (font != null) {
            validateFontName("font.bodyEastAsia", font.getBodyEastAsia());
            validateFontName("font.bodyAscii", font.getBodyAscii());
            validateFontName("font.headingEastAsia", font.getHeadingEastAsia());
            validateFontName("font.headingAscii", font.getHeadingAscii());
            validateFontName("font.heading1EastAsia", font.getHeading1EastAsia());
            validateFontName("font.heading2EastAsia", font.getHeading2EastAsia());
            validateFontName("font.heading3EastAsia", font.getHeading3EastAsia());
            validateFontName("font.heading4EastAsia", font.getHeading4EastAsia());
            validateFontName("font.heading5EastAsia", font.getHeading5EastAsia());
            validateFontName("font.tableEastAsia", font.getTableEastAsia());
            validateFontName("font.tableAscii", font.getTableAscii());
            validateFontName("font.code", font.getCode());
            validateFontName("font.footerEastAsia", font.getFooterEastAsia());
            validateFontName("font.abstractEastAsia", font.getAbstractEastAsia());
            validateFontName("font.keywordEastAsia", font.getKeywordEastAsia());
            validateFontName("font.referenceEastAsia", font.getReferenceEastAsia());
            validateFontName("font.acknowledgmentEastAsia", font.getAcknowledgmentEastAsia());
        }
    }

    public static PaperFormatConfig parseJson(String json) {
        if (StringUtils.isBlank(json)) {
            return new PaperFormatConfig();
        }
        try {
            PaperFormatConfig config = MAPPER.readValue(json, PaperFormatConfig.class);
            return config != null ? config : new PaperFormatConfig();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid PaperFormatConfig JSON", e);
        }
    }

    public static String toJson(PaperFormatConfig config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static void overlay(PaperFormatConfig base, PaperFormatConfig overlay) {
        mergePage(base.getPage(), overlay.nestedPage());
        mergeFont(base.getFont(), overlay.nestedFont());
        mergeFontSize(base.getFontSize(), overlay.nestedFontSize());
        mergeParagraph(base.getParagraph(), overlay.nestedParagraph());
        mergeHeading(base.getHeading(), overlay.nestedHeading());
        mergeHeaderFooter(base.getHeaderFooter(), overlay.nestedHeaderFooter());
        mergeExport(base.getExport(), overlay.nestedExport());
    }

    private static void mergePage(PaperFormatConfig.Page base, PaperFormatConfig.Page overlay) {
        if (overlay == null) {
            return;
        }
        if (overlay.getPaper() != null) {
            base.setPaper(overlay.getPaper());
        }
        if (overlay.getMarginTopMm() != null) {
            base.setMarginTopMm(overlay.getMarginTopMm());
        }
        if (overlay.getMarginBottomMm() != null) {
            base.setMarginBottomMm(overlay.getMarginBottomMm());
        }
        if (overlay.getMarginLeftMm() != null) {
            base.setMarginLeftMm(overlay.getMarginLeftMm());
        }
        if (overlay.getMarginRightMm() != null) {
            base.setMarginRightMm(overlay.getMarginRightMm());
        }
    }

    private static void mergeFont(PaperFormatConfig.Font base, PaperFormatConfig.Font overlay) {
        if (overlay == null) {
            return;
        }
        if (overlay.getBodyEastAsia() != null) {
            base.setBodyEastAsia(overlay.getBodyEastAsia());
        }
        if (overlay.getBodyAscii() != null) {
            base.setBodyAscii(overlay.getBodyAscii());
        }
        if (overlay.getHeadingEastAsia() != null) {
            base.setHeadingEastAsia(overlay.getHeadingEastAsia());
        }
        if (overlay.getHeadingAscii() != null) {
            base.setHeadingAscii(overlay.getHeadingAscii());
        }
        if (overlay.getHeading1EastAsia() != null) {
            base.setHeading1EastAsia(overlay.getHeading1EastAsia());
        }
        if (overlay.getHeading2EastAsia() != null) {
            base.setHeading2EastAsia(overlay.getHeading2EastAsia());
        }
        if (overlay.getHeading3EastAsia() != null) {
            base.setHeading3EastAsia(overlay.getHeading3EastAsia());
        }
        if (overlay.getHeading4EastAsia() != null) {
            base.setHeading4EastAsia(overlay.getHeading4EastAsia());
        }
        if (overlay.getHeading5EastAsia() != null) {
            base.setHeading5EastAsia(overlay.getHeading5EastAsia());
        }
        if (overlay.getTableEastAsia() != null) {
            base.setTableEastAsia(overlay.getTableEastAsia());
        }
        if (overlay.getTableAscii() != null) {
            base.setTableAscii(overlay.getTableAscii());
        }
        if (overlay.getCode() != null) {
            base.setCode(overlay.getCode());
        }
        if (overlay.getFooterEastAsia() != null) {
            base.setFooterEastAsia(overlay.getFooterEastAsia());
        }
        if (overlay.getAbstractEastAsia() != null) {
            base.setAbstractEastAsia(overlay.getAbstractEastAsia());
        }
        if (overlay.getKeywordEastAsia() != null) {
            base.setKeywordEastAsia(overlay.getKeywordEastAsia());
        }
        if (overlay.getReferenceEastAsia() != null) {
            base.setReferenceEastAsia(overlay.getReferenceEastAsia());
        }
        if (overlay.getAcknowledgmentEastAsia() != null) {
            base.setAcknowledgmentEastAsia(overlay.getAcknowledgmentEastAsia());
        }
    }

    private static void mergeFontSize(PaperFormatConfig.FontSize base, PaperFormatConfig.FontSize overlay) {
        if (overlay == null) {
            return;
        }
        if (overlay.getTitle() != null) {
            base.setTitle(overlay.getTitle());
        }
        if (overlay.getHeading1() != null) {
            base.setHeading1(overlay.getHeading1());
        }
        if (overlay.getHeading2() != null) {
            base.setHeading2(overlay.getHeading2());
        }
        if (overlay.getHeading3() != null) {
            base.setHeading3(overlay.getHeading3());
        }
        if (overlay.getHeading4() != null) {
            base.setHeading4(overlay.getHeading4());
        }
        if (overlay.getHeading5() != null) {
            base.setHeading5(overlay.getHeading5());
        }
        if (overlay.getBody() != null) {
            base.setBody(overlay.getBody());
        }
        if (overlay.getAbstractLabel() != null) {
            base.setAbstractLabel(overlay.getAbstractLabel());
        }
        if (overlay.getAbstractBody() != null) {
            base.setAbstractBody(overlay.getAbstractBody());
        }
        if (overlay.getKeyword() != null) {
            base.setKeyword(overlay.getKeyword());
        }
        if (overlay.getCaption() != null) {
            base.setCaption(overlay.getCaption());
        }
        if (overlay.getReference() != null) {
            base.setReference(overlay.getReference());
        }
        if (overlay.getAcknowledgment() != null) {
            base.setAcknowledgment(overlay.getAcknowledgment());
        }
        if (overlay.getFooter() != null) {
            base.setFooter(overlay.getFooter());
        }
        if (overlay.getToc() != null) {
            base.setToc(overlay.getToc());
        }
    }

    private static void mergeParagraph(PaperFormatConfig.Paragraph base, PaperFormatConfig.Paragraph overlay) {
        if (overlay == null) {
            return;
        }
        if (overlay.getLineSpacingPt() != null) {
            base.setLineSpacingPt(overlay.getLineSpacingPt());
        }
        if (overlay.getLineSpacingRule() != null) {
            base.setLineSpacingRule(overlay.getLineSpacingRule());
        }
        if (overlay.getLineSpacingMultiple() != null) {
            base.setLineSpacingMultiple(overlay.getLineSpacingMultiple());
        }
        if (overlay.getFirstLineIndentChars() != null) {
            base.setFirstLineIndentChars(overlay.getFirstLineIndentChars());
        }
        if (overlay.getBodyAlign() != null) {
            base.setBodyAlign(overlay.getBodyAlign());
        }
    }

    private static void mergeHeading(PaperFormatConfig.Heading base, PaperFormatConfig.Heading overlay) {
        if (overlay == null) {
            return;
        }
        if (overlay.getH1Align() != null) {
            base.setH1Align(overlay.getH1Align());
        }
        if (overlay.getH2Align() != null) {
            base.setH2Align(overlay.getH2Align());
        }
        if (overlay.getH3Align() != null) {
            base.setH3Align(overlay.getH3Align());
        }
        if (overlay.getH1Bold() != null) {
            base.setH1Bold(overlay.getH1Bold());
        }
        if (overlay.getH2Bold() != null) {
            base.setH2Bold(overlay.getH2Bold());
        }
        if (overlay.getH3Bold() != null) {
            base.setH3Bold(overlay.getH3Bold());
        }
        if (overlay.getTitleBold() != null) {
            base.setTitleBold(overlay.getTitleBold());
        }
        if (overlay.getH1SpacingBeforePt() != null) {
            base.setH1SpacingBeforePt(overlay.getH1SpacingBeforePt());
        }
        if (overlay.getH1SpacingAfterPt() != null) {
            base.setH1SpacingAfterPt(overlay.getH1SpacingAfterPt());
        }
        if (overlay.getH2SpacingBeforePt() != null) {
            base.setH2SpacingBeforePt(overlay.getH2SpacingBeforePt());
        }
        if (overlay.getH2SpacingAfterPt() != null) {
            base.setH2SpacingAfterPt(overlay.getH2SpacingAfterPt());
        }
        if (overlay.getH3SpacingBeforePt() != null) {
            base.setH3SpacingBeforePt(overlay.getH3SpacingBeforePt());
        }
        if (overlay.getH3SpacingAfterPt() != null) {
            base.setH3SpacingAfterPt(overlay.getH3SpacingAfterPt());
        }
    }

    private static void mergeHeaderFooter(PaperFormatConfig.HeaderFooter base, PaperFormatConfig.HeaderFooter overlay) {
        if (overlay == null) {
            return;
        }
        if (overlay.getOddHeader() != null) {
            base.setOddHeader(overlay.getOddHeader());
        }
        if (overlay.getEvenHeader() != null) {
            base.setEvenHeader(overlay.getEvenHeader());
        }
        if (overlay.getFooterFormat() != null) {
            base.setFooterFormat(overlay.getFooterFormat());
        }
    }

    private static void mergeExport(PaperFormatConfig.Export base, PaperFormatConfig.Export overlay) {
        if (overlay == null) {
            return;
        }
        if (overlay.getPatchTemplateStyles() != null) {
            base.setPatchTemplateStyles(overlay.getPatchTemplateStyles());
        }
        if (overlay.getApplyPageSetup() != null) {
            base.setApplyPageSetup(overlay.getApplyPageSetup());
        }
    }

    private static void validateMargin(String field, Double value) {
        if (value == null) {
            return;
        }
        if (value < MARGIN_MIN_MM || value > MARGIN_MAX_MM) {
            throw new IllegalArgumentException(field + " must be between "
                + MARGIN_MIN_MM + " and " + MARGIN_MAX_MM + " mm");
        }
    }

    private static void validateFontSize(String field, Double value) {
        if (value == null) {
            return;
        }
        if (value < FONT_SIZE_MIN_PT || value > FONT_SIZE_MAX_PT) {
            throw new IllegalArgumentException(field + " must be between "
                + FONT_SIZE_MIN_PT + " and " + FONT_SIZE_MAX_PT + " pt");
        }
    }

    private static void validateLineSpacingPt(String field, Double value) {
        if (value == null) {
            return;
        }
        if (value < LINE_SPACING_MIN_PT || value > LINE_SPACING_MAX_PT) {
            throw new IllegalArgumentException(field + " must be between "
                + LINE_SPACING_MIN_PT + " and " + LINE_SPACING_MAX_PT + " pt");
        }
    }

    private static void validateLineSpacingMultiple(String field, Double value) {
        if (value == null) {
            return;
        }
        if (value < LINE_SPACING_MULTIPLE_MIN || value > LINE_SPACING_MULTIPLE_MAX) {
            throw new IllegalArgumentException(field + " must be between "
                + LINE_SPACING_MULTIPLE_MIN + " and " + LINE_SPACING_MULTIPLE_MAX);
        }
    }

    private static void validateFontName(String field, String value) {
        if (value == null) {
            return;
        }
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
