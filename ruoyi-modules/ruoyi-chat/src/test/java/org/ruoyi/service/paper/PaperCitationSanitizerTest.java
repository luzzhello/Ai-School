package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;
import org.ruoyi.domain.paper.Reference;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperCitationSanitizerTest {

    @Test
    void keepsValidAndDropsInvalidIndexes() {
        Set<Integer> valid = Set.of(1, 2, 3);
        String out = PaperCitationSanitizer.sanitizeToValidIndexes(
            "研究表明……仍有不足[1]。另有观点[9]。综合对比[1,2,9]与区间[1-3]及[2-5]。",
            valid);
        assertTrue(out.contains("[1]"));
        assertFalse(out.contains("[9]"));
        assertTrue(out.contains("[1,2]") || out.contains("[1-2]"));
        assertTrue(out.contains("[1-3]"));
        assertTrue(out.contains("[2,3]") || out.contains("[2-3]"));
    }

    @Test
    void stripsAllWhenNoReferences() {
        String out = PaperCitationSanitizer.sanitizeToValidIndexes("背景分析[1][2]。", Set.of());
        assertEquals("背景分析。", out);
    }

    @Test
    void collectValidIndexesIgnoresNull() {
        Reference a = new Reference();
        a.setIndex(1);
        Reference b = new Reference();
        b.setIndex(null);
        assertEquals(Set.of(1), PaperCitationSanitizer.collectValidIndexes(List.of(a, b)));
    }
}
