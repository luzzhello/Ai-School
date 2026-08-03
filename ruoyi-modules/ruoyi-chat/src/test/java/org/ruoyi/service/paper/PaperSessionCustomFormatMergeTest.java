package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;

import static org.junit.jupiter.api.Assertions.*;

class PaperSessionCustomFormatMergeTest {

    @Test
    void customMode_mergesDefaultThenCustomThenOverride() {
        PaperFormatConfig custom = new PaperFormatConfig();
        custom.getFont().setBodyEastAsia("仿宋");
        custom.getFontSize().setBody(12.0);

        PaperFormatConfig override = new PaperFormatConfig();
        override.getFontSize().setBody(14.0);

        PaperFormatConfig effective = PaperFormatMerger.merge(
            PaperFormatDefaults.dalianOcean(), custom, override);
        assertEquals("仿宋", effective.getFont().getBodyEastAsia());
        assertEquals(14.0, effective.getFontSize().getBody());
    }

    @Test
    void applyCustomPatchFlag_setsExportPatchTemplateStyles() {
        PaperFormatConfig effective = PaperFormatDefaults.dalianOcean();
        PaperSessionCustomFormatService.applyPatchFlag(effective, 0);
        assertEquals(Boolean.FALSE, effective.getExport().getPatchTemplateStyles());

        PaperFormatConfig effective2 = PaperFormatDefaults.dalianOcean();
        PaperSessionCustomFormatService.applyPatchFlag(effective2, 1);
        assertEquals(Boolean.TRUE, effective2.getExport().getPatchTemplateStyles());
    }
}
