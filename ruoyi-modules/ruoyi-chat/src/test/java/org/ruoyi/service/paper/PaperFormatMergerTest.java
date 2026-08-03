package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;

import static org.junit.jupiter.api.Assertions.*;

class PaperFormatMergerTest {

    @Test
    void merge_overridesOnlyProvidedFields() {
        PaperFormatConfig base = PaperFormatDefaults.dalianOcean();
        PaperFormatConfig override = new PaperFormatConfig();
        override.getFontSize().setBody(12.0);
        PaperFormatConfig effective = PaperFormatMerger.merge(base, override);
        assertEquals(12.0, effective.getFontSize().getBody());
        assertEquals(PaperFormatDefaults.dalianOcean().getFont().getBodyEastAsia(),
            effective.getFont().getBodyEastAsia());
        assertEquals(30.0, effective.getPage().getMarginTopMm());
    }

    @Test
    void merge_threeLevels_templateThenSession() {
        PaperFormatConfig def = PaperFormatDefaults.dalianOcean();
        PaperFormatConfig template = new PaperFormatConfig();
        template.getFont().setBodyEastAsia("仿宋");
        template.getFontSize().setBody(12.0);
        PaperFormatConfig session = new PaperFormatConfig();
        session.getFontSize().setBody(14.0);
        PaperFormatConfig effective = PaperFormatMerger.merge(def, template, session);
        assertEquals("仿宋", effective.getFont().getBodyEastAsia());
        assertEquals(14.0, effective.getFontSize().getBody());
    }

    @Test
    void validate_rejectsOutOfRangeMargin() {
        PaperFormatConfig c = PaperFormatDefaults.dalianOcean();
        c.getPage().setMarginTopMm(100.0);
        assertThrows(IllegalArgumentException.class, () -> PaperFormatMerger.validate(c));
    }

    @Test
    void parseJson_sparseRoundTrip_staysSparseAndMergesWithDefaults() {
        PaperFormatConfig override = PaperFormatMerger.parseJson("{\"fontSize\":{\"body\":12.0}}");
        String json = PaperFormatMerger.toJson(override);
        assertEquals("{\"fontSize\":{\"body\":12.0}}", json);
        assertNull(override.nestedPage());
        assertNull(override.nestedFont());

        PaperFormatConfig effective = PaperFormatMerger.merge(PaperFormatDefaults.dalianOcean(), override);
        assertEquals(12.0, effective.getFontSize().getBody());
        assertEquals("宋体", effective.getFont().getBodyEastAsia());
    }

    @Test
    void parseJson_blankOrNull_returnsEmptyConfig() {
        PaperFormatConfig blank = PaperFormatMerger.parseJson("");
        assertNull(blank.nestedPage());
        assertNull(blank.nestedFontSize());

        PaperFormatConfig nullJson = PaperFormatMerger.parseJson(null);
        assertNull(nullJson.nestedPage());
        assertNull(nullJson.nestedFontSize());
    }

    @Test
    void validate_acceptsDalianOceanDefaults() {
        assertDoesNotThrow(() -> PaperFormatMerger.validate(PaperFormatDefaults.dalianOcean()));
    }

    @Test
    void parseJson_invalidJson_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> PaperFormatMerger.parseJson("{not-json"));
    }
}
