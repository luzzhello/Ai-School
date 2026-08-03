package org.ruoyi.service.paper;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.domain.paper.PaperUiScreenshotImage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class PaperUiScreenshotInjectorTest {

    @Test
    void buildCaptionUsesNumberedFigureFormat() {
        assertEquals("图 5.2 骑行活动管理列表界面",
            PaperUiScreenshotInjector.buildCaption("5.1.1 骑行活动管理功能", "列表", 5, 2));
        assertEquals("图 5.3 活动报名界面",
            PaperUiScreenshotInjector.buildCaption("活动报名", "其他", 5, 3));
    }

    @Test
    void injectAllReplacesPlaceholderWithNumberedCaptions() {
        PaperUiScreenshotImage a = img("a.png", "列表");
        PaperUiScreenshotImage b = img("b.png", "新增");
        String content = "说明。\n【此处插入骑行活动管理列表界面截图】\n【此处插入骑行活动管理新增界面截图】\n";
        String out = PaperUiScreenshotInjector.injectAll(content, List.of(a, b), "骑行活动管理", 5, 2);
        assertTrue(out.contains("![图 5.2 骑行活动管理列表界面](a.png)"));
        assertTrue(out.contains("![图 5.3 骑行活动管理新增界面](b.png)"));
    }

    @Test
    void normalizeMarkdownImagesAsBlocksPullsInlineImageOutOfSentence() {
        String raw = "前文说明。如下图所示。![论坛管理列表界面截图](//api/paper/assets/a.png), 图中展示筛选控件。";
        String out = PaperUiScreenshotInjector.normalizeMarkdownImagesAsBlocks(raw);
        assertTrue(out.contains("前文说明。如下图所示。"));
        assertTrue(out.contains("![论坛管理列表界面截图](/api/paper/assets/a.png)"));
        assertTrue(out.contains(", 图中展示筛选控件。"));
        assertTrue(out.indexOf("![论坛管理列表界面截图](/api/paper/assets/a.png)")
            > out.indexOf("前文说明"));
        // 图片应独占一行（前后有换行分隔）
        assertTrue(out.contains("\n![论坛管理列表界面截图](/api/paper/assets/a.png)\n"));
    }

    @Test
    void nextFigureIndexContinuesFromExistingBodies() {
        int next = PaperUiScreenshotInjector.nextFigureIndex(
            5,
            Map.of("c1", "上文 ![图 5.1 用户管理界面](x.png)"),
            "当前无图");
        assertEquals(2, next);
    }

    private static PaperUiScreenshotImage img(String url, String label) {
        PaperUiScreenshotImage image = new PaperUiScreenshotImage();
        image.setAssetUrl(url);
        image.setLabel(label);
        return image;
    }
}
