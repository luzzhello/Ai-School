package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleKeywordSplitterTest {

    @Test
    void splitsSpringBootThesisTitle() {
        List<String> kws = TitleKeywordSplitter.split(
            "基于SpringBoot的学生选课系统设计与实现", 3, 5);

        assertTrue(kws.size() >= 3 && kws.size() <= 5);
        assertTrue(kws.stream().anyMatch(k -> k.equalsIgnoreCase("SpringBoot")));
        assertTrue(kws.contains("学生选课") || kws.contains("选课"));
        assertEquals(new LinkedHashSet<>(kws).size(), kws.size());
    }
}
