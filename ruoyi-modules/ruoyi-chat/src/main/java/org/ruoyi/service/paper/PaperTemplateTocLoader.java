package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperTemplateStyleMapping;
import org.ruoyi.domain.paper.TocNode;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从当前论文模板 docx（{@link PaperTemplateService}）解析默认论文大纲。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperTemplateTocLoader {

    private static final Pattern SKIP_TITLE = Pattern.compile(
        "模板--|注意事项|^(模板[一二三])|（模板");
    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s*");
    private static final Pattern CN_CHAPTER = Pattern.compile("^[一二三四五六七八九十百]+[、．.\\s]+");

    private final PaperTemplateService paperTemplateService;

    /**
     * 加载默认模板大纲；解析失败时回退 {@link PaperStandardTocTemplate}。
     */
    public List<TocNode> load(List<String> tables) {
        List<HeadingLine> lines = parseHeadingsFromDocx();
        if (lines.isEmpty()) {
            log.warn("论文模板 docx 未解析到有效标题，回退内置标准大纲");
            return PaperStandardTocTemplate.build(tables);
        }
        List<TocNode> toc = buildTree(lines);
        log.info("已从论文模板加载大纲，根节点 {} 个", toc.size());
        return toc;
    }

    private List<HeadingLine> parseHeadingsFromDocx() {
        PaperTemplateStyleMapping mapping = paperTemplateService.getStyleMapping();
        List<HeadingLine> lines = new ArrayList<>();
        try (InputStream in = paperTemplateService.openTemplateInputStream();
             XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String styleId = paragraph.getStyleID();
                if (styleId == null) {
                    continue;
                }
                int level = mapping.levelOfHeadingStyle(styleId);
                if (level < 0) {
                    continue;
                }
                String text = paragraph.getText();
                if (StringUtils.isBlank(text)) {
                    continue;
                }
                text = text.strip();
                if (shouldSkip(text)) {
                    continue;
                }
                lines.add(new HeadingLine(level, text));
            }
        } catch (Exception e) {
            log.error("读取论文模板 docx 失败: {}", e.getMessage());
            return List.of();
        }
        return lines;
    }

    private boolean shouldSkip(String title) {
        String trimmed = title.trim();
        if ("Abstract".equalsIgnoreCase(trimmed) || "目录".equals(trimmed) || "Contents".equalsIgnoreCase(trimmed)) {
            return true;
        }
        return SKIP_TITLE.matcher(title).find();
    }

    private List<TocNode> buildTree(List<HeadingLine> lines) {
        List<TocNode> roots = new ArrayList<>();
        Deque<TocNode> stack = new ArrayDeque<>();
        int[] chapterCounter = new int[4];

        for (HeadingLine line : lines) {
            TocNode node = new TocNode();
            node.setLevel(line.level());
            node.setTitle(normalizeTitle(line.title(), line.level(), chapterCounter));
            node.setStatus("pending");
            node.setGenerated(false);
            node.setChildren(new ArrayList<>());

            while (!stack.isEmpty() && stack.peek().getLevel() >= line.level()) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.peek().getChildren().add(node);
            }
            stack.push(node);
        }
        return roots;
    }

    private String normalizeTitle(String raw, int level, int[] chapterCounter) {
        String text = raw.strip();
        if (level == 1 && CN_CHAPTER.matcher(text).lookingAt()) {
            chapterCounter[1]++;
            for (int i = 2; i < chapterCounter.length; i++) {
                chapterCounter[i] = 0;
            }
            String bare = CN_CHAPTER.matcher(text).replaceFirst("").trim();
            return chapterCounter[1] + " " + bare;
        }
        if (level >= 2) {
            Matcher m = NUMERIC_PREFIX.matcher(text);
            if (m.find()) {
                String prefix = m.group(1);
                String bare = text.substring(m.end()).strip();
                return prefix + " " + bare;
            }
        }
        return text;
    }

    private record HeadingLine(int level, String title) {
    }
}
