package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LitQueryNormalizerTest {

    @Test
    void extracts_springboot_vue_cycling_site_design() {
        List<String> terms = LitQueryNormalizer.extractTerms("基于 Springboot+vue 的骑行网站设计与实现");
        String lower = terms.stream().map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
        assertTrue(lower.contains("springboot"), () -> "terms=" + terms);
        assertTrue(lower.contains("vue"), () -> "terms=" + terms);
        assertTrue(terms.contains("骑行"), () -> "terms=" + terms);
        assertTrue(terms.contains("网站设计"), () -> "terms=" + terms);
        assertFalse(terms.stream().anyMatch(t -> t.contains("+")));
        assertFalse(terms.contains("设计与实现"));
        assertEquals("Springboot vue 骑行 网站设计", LitQueryNormalizer.toSearchQuery(
            "基于 Springboot+vue 的骑行网站设计与实现"));
    }

    @Test
    void extracts_without_space_variant() {
        assertEquals(
            "Springboot vue 骑行 网站设计",
            LitQueryNormalizer.toSearchQuery("基于Springboot+vue的骑行网站设计与实现"));
    }

    @Test
    void strips_design_shell_and_keeps_core() {
        String q = LitQueryNormalizer.toSearchQuery("基于Spring Boot的高校教务管理系统的设计与实现");
        assertTrue(q.toLowerCase(Locale.ROOT).contains("spring"));
        assertTrue(q.toLowerCase(Locale.ROOT).contains("boot"));
        assertTrue(q.contains("高校") || q.contains("教务管理系统") || q.contains("教务管理"),
            () -> "q=" + q);
        assertFalse(q.contains("设计与实现"));
    }

    @Test
    void keeps_short_keyword() {
        assertEquals("微服务", LitQueryNormalizer.toSearchQuery("微服务"));
    }

    @Test
    void splits_plus_joined_tech_stack() {
        List<String> terms = LitQueryNormalizer.extractTerms("Springboot+vue+Mybatis");
        assertTrue(terms.stream().anyMatch(t -> t.equalsIgnoreCase("Springboot")));
        assertTrue(terms.stream().anyMatch(t -> t.equalsIgnoreCase("vue")));
        assertTrue(terms.stream().anyMatch(t -> t.equalsIgnoreCase("Mybatis")));
    }
}
