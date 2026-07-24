package org.ruoyi.domain.paper;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 从模板 docx 解压后的 styles.xml 解析出的样式 ID 映射。
 */
@Data
@Builder
public class PaperTemplateStyleMapping {

    private String normal;
    private String heading1;
    private String heading2;
    private String heading3;
    private String toc1;
    private String toc2;
    private String toc3;
    private String reference;

    public static PaperTemplateStyleMapping defaults() {
        return PaperTemplateStyleMapping.builder()
            .normal("1")
            .heading1("2")
            .heading2("3")
            .heading3("4")
            .toc1("7")
            .toc2("8")
            .toc3("6")
            .reference("13")
            .build();
    }

    public String headingStyleId(int level) {
        return switch (Math.max(1, Math.min(level, 5))) {
            case 1 -> heading1;
            case 2 -> heading2;
            // 模板通常仅到 Heading3；四/五级套用三级样式，字号字体由 run 属性覆盖
            default -> heading3;
        };
    }

    public String tocStyleId(int level) {
        return switch (level) {
            case 1 -> toc1;
            case 2 -> toc2;
            default -> toc3;
        };
    }

    public Set<String> headingStyleIds() {
        return Set.of(heading1, heading2, heading3);
    }

    public int levelOfHeadingStyle(String styleId) {
        if (heading1.equals(styleId)) {
            return 1;
        }
        if (heading2.equals(styleId)) {
            return 2;
        }
        if (heading3.equals(styleId)) {
            return 3;
        }
        return -1;
    }
}
