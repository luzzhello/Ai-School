package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * resolveEffective 的纯合并语义（无 DB）：null 模板 / 空 overlay → 大连默认。
 */
class PaperFormatTemplateResolveEffectiveTest {

    @Test
    void merge_nullOverlays_returnsDalianDefaults() {
        PaperFormatConfig def = PaperFormatDefaults.dalianOcean();
        PaperFormatConfig templateOverlay = new PaperFormatConfig();
        PaperFormatConfig sessionOverlay = PaperFormatMerger.parseJson(null);
        PaperFormatConfig effective = PaperFormatMerger.merge(def, templateOverlay, sessionOverlay);
        PaperFormatMerger.validate(effective);

        assertEquals(10.5, effective.getFontSize().getBody());
        assertEquals(30.0, effective.getPage().getMarginTopMm());
        assertEquals("宋体", effective.getFont().getBodyEastAsia());
        assertEquals(18.0, effective.getParagraph().getLineSpacingPt());
    }
}
