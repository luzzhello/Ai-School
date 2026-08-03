package org.ruoyi.service.paper;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperUiScreenshot;
import org.ruoyi.domain.paper.PaperUiScreenshotImage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class PaperScreenshotServiceContextTest {

    @Test
    void buildSystemContextIncludesTitleTablesAndCode() {
        PaperSession session = new PaperSession();
        session.setTitle("基于SpringBoot的学生选课系统");
        PaperSession.UserInputs inputs = new PaperSession.UserInputs();
        inputs.setCodeContent("public class CourseController {}");
        inputs.setEnvInfo("Java / SpringBoot / MySQL");
        session.setUserInputs(inputs);

        PaperSession.SqlParsed parsed = new PaperSession.SqlParsed();
        parsed.setTables(List.of("sys_user", "course_info"));
        Map<String, String> comments = new LinkedHashMap<>();
        comments.put("sys_user", "用户");
        comments.put("course_info", "课程信息");
        parsed.setTableComments(comments);
        parsed.setSummary("用户选课与课程管理");
        session.setSqlParsed(parsed);

        String ctx = PaperScreenshotService.buildSystemContext(session);
        assertTrue(ctx.contains("学生选课"));
        assertTrue(ctx.contains("sys_user"));
        assertTrue(ctx.contains("课程信息"));
        assertTrue(ctx.contains("CourseController"));
        assertTrue(ctx.contains("SpringBoot"));
        assertFalse(ctx.isBlank());
    }

    @Test
    void buildAnalyzeMessageCoversFullPartialAndFail() {
        assertEquals("已识别 3 张截图的功能名与界面类型，可再手动调整",
            PaperScreenshotService.buildAnalyzeMessage(3, 3, 0, true));
        assertEquals("部分识别成功：成功 2 张，失败 1 张，可手动调整后重试失败项",
            PaperScreenshotService.buildAnalyzeMessage(3, 2, 1, true));
        assertEquals("识别失败：共 3 张均未得到有效结果，请检查视觉模型或稍后重试",
            PaperScreenshotService.buildAnalyzeMessage(3, 0, 3, true));
        assertEquals("视觉模型不可用，识别未执行",
            PaperScreenshotService.buildAnalyzeMessage(3, 0, 3, false));
        assertTrue(PaperScreenshotService.buildAnalyzeMessage(3, 3, 0, true, 2)
            .contains("已自动拆成多组（+2）"));
    }

    @Test
    void normalizeFeatureTitleStripsTrailingGongNeng() {
        assertEquals("活动管理", PaperScreenshotService.normalizeFeatureTitle("活动管理功能"));
        assertEquals("活动管理", PaperScreenshotService.normalizeFeatureTitle(" 活动管理 "));
    }

    @Test
    void splitGroupsWhenSameCardHasMultipleTitles() {
        PaperUiScreenshot group = group("uss_1", "admin", "",
            image("usi_a", "a.png"),
            image("usi_b", "b.png"),
            image("usi_c", "c.png"));
        Map<String, String> titles = Map.of(
            "usi_a", "活动管理",
            "usi_b", "骑行路线管理",
            "usi_c", "论坛管理"
        );

        List<PaperUiScreenshot> result = PaperScreenshotService.splitGroupsByRecognizedTitles(
            List.of(group), titles);

        assertEquals(3, result.size());
        assertEquals("活动管理", result.get(0).getTitle());
        assertEquals("骑行路线管理", result.get(1).getTitle());
        assertEquals("论坛管理", result.get(2).getTitle());
        assertEquals("uss_1", result.get(0).getId());
        assertEquals(1, result.get(0).getImages().size());
        assertEquals(1, result.get(1).getImages().size());
        assertEquals(1, result.get(2).getImages().size());
        assertEquals(1, result.get(0).getSort());
        assertEquals(2, result.get(1).getSort());
        assertEquals(3, result.get(2).getSort());
    }

    @Test
    void doesNotSplitWhenAllImagesShareOneTitle() {
        PaperUiScreenshot group = group("uss_1", "admin", "",
            image("usi_a", "a.png"),
            image("usi_b", "b.png"));
        Map<String, String> titles = Map.of(
            "usi_a", "用户管理",
            "usi_b", "用户管理功能"
        );

        List<PaperUiScreenshot> result = PaperScreenshotService.splitGroupsByRecognizedTitles(
            List.of(group), titles);

        assertEquals(1, result.size());
        assertEquals("用户管理", result.get(0).getTitle());
        assertEquals(2, result.get(0).getImages().size());
    }

    @Test
    void untitledImagesGoToFallbackGroupWhenSplitting() {
        PaperUiScreenshot group = group("uss_1", "admin", "杂项",
            image("usi_a", "a.png"),
            image("usi_b", "b.png"),
            image("usi_c", "c.png"));
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put("usi_a", "活动管理");
        titles.put("usi_b", "评论管理");
        // usi_c 无识别结果

        List<PaperUiScreenshot> result = PaperScreenshotService.splitGroupsByRecognizedTitles(
            List.of(group), titles);

        assertEquals(3, result.size());
        assertEquals("活动管理", result.get(0).getTitle());
        assertEquals("评论管理", result.get(1).getTitle());
        assertEquals("杂项", result.get(2).getTitle());
        assertEquals("usi_c", result.get(2).getImages().get(0).getId());
    }

    private static PaperUiScreenshot group(
        String id, String module, String title, PaperUiScreenshotImage... images
    ) {
        PaperUiScreenshot g = new PaperUiScreenshot();
        g.setId(id);
        g.setModule(module);
        g.setTitle(title);
        g.setSort(1);
        g.setImages(new ArrayList<>(List.of(images)));
        return g;
    }

    private static PaperUiScreenshotImage image(String id, String url) {
        PaperUiScreenshotImage img = new PaperUiScreenshotImage();
        img.setId(id);
        img.setAssetUrl(url);
        img.setLabel("列表");
        img.setSort(1);
        return img;
    }
}
