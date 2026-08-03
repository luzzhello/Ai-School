package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.TocNode;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 论文会话 → 答辩 PPT（.pptx）。
 * <p>
 * 借鉴 nature-paper2ppt：按论文结构拆页、每页一论点、要点 3–5 条，不把整章正文贴进幻灯片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperDefensePptService {

    private static final int SLIDE_W = 1280;
    private static final int SLIDE_H = 720;
    private static final int MAX_SLIDES = 18;
    private static final int MAX_BULLETS = 5;
    private static final int MAX_BULLET_CHARS = 48;

    private final PaperSessionStore paperSessionStore;

    public byte[] export(String sessionId) {
        PaperSession session = paperSessionStore.get(sessionId);
        if (session == null) {
            throw new ServiceException("会话不存在或已过期");
        }
        String title = StringUtils.isNotBlank(session.getTitle()) ? session.getTitle().trim() : "毕业论文答辩";
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new Dimension(SLIDE_W, SLIDE_H));
            int slideBudget = MAX_SLIDES;

            addTitleSlide(ppt, title, resolveEnvOneLiner(session));
            slideBudget--;

            List<String> outlineBullets = collectLevel1Titles(session.getToc());
            if (!outlineBullets.isEmpty() && slideBudget > 1) {
                addBulletSlide(ppt, "答辩提纲", outlineBullets, "按章节依次介绍研究背景、需求、设计、实现、测试与总结。");
                slideBudget--;
            }

            List<TocNode> contentRoots = collectContentChapterRoots(session.getToc());
            int remainingChapters = Math.max(1, contentRoots.size());
            for (TocNode root : contentRoots) {
                if (slideBudget <= 1) {
                    break;
                }
                int allot = Math.max(1, (slideBudget - 1) / remainingChapters);
                allot = Math.min(allot, 3);
                int used = addChapterSlides(ppt, root, session.getGeneratedContent(), allot);
                slideBudget -= used;
                remainingChapters--;
            }

            addClosingSlide(ppt, title);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("导出答辩 PPT 失败 sessionId={}", sessionId, e);
            throw new ServiceException("导出答辩 PPT 失败");
        }
    }

    public String resolveTitle(String sessionId) {
        PaperSession session = paperSessionStore.get(sessionId);
        String title = session == null ? null : session.getTitle();
        return StringUtils.isBlank(title) ? "答辩PPT" : title.trim();
    }

    private int addChapterSlides(XMLSlideShow ppt, TocNode root, Map<String, String> contents, int maxSlides) {
        if (root == null || maxSlides <= 0) {
            return 0;
        }
        List<TocNode> leaves = collectWritableLeaves(root);
        if (leaves.isEmpty()) {
            leaves = List.of(root);
        }
        int used = 0;
        int perSlide = Math.max(1, (int) Math.ceil(leaves.size() / (double) maxSlides));
        for (int i = 0; i < leaves.size() && used < maxSlides; i += perSlide) {
            List<TocNode> batch = leaves.subList(i, Math.min(i + perSlide, leaves.size()));
            TocNode focus = batch.get(0);
            String slideTitle = shortTitle(focus.getTitle());
            List<String> bullets = new ArrayList<>();
            for (TocNode node : batch) {
                bullets.addAll(extractBullets(contents == null ? null : contents.get(node.getId()), 2));
                if (bullets.size() >= MAX_BULLETS) {
                    break;
                }
            }
            if (bullets.isEmpty()) {
                bullets.add(shortTitle(root.getTitle()) + "（待补充要点）");
            }
            if (bullets.size() > MAX_BULLETS) {
                bullets = new ArrayList<>(bullets.subList(0, MAX_BULLETS));
            }
            String note = "围绕「" + slideTitle + "」说明设计/实现要点，控制在 30–60 秒。";
            addBulletSlide(ppt, slideTitle, bullets, note);
            used++;
        }
        return used;
    }

    private void addTitleSlide(XMLSlideShow ppt, String title, String subtitle) {
        XSLFSlide slide = ppt.createSlide();
        paintBackground(slide, new Color(0xF8, 0xFA, 0xFC));
        addTextBox(slide, title, new Rectangle(80, 220, 1120, 120), 36.0, true, new Color(0x1E, 0x29, 0x3B),
            TextParagraph.TextAlign.CENTER);
        addTextBox(slide, "毕业设计答辩", new Rectangle(80, 360, 1120, 40), 18.0, false, new Color(0x47, 0x55, 0x69),
            TextParagraph.TextAlign.CENTER);
        if (StringUtils.isNotBlank(subtitle)) {
            addTextBox(slide, subtitle, new Rectangle(80, 420, 1120, 40), 14.0, false, new Color(0x64, 0x74, 0x8B),
                TextParagraph.TextAlign.CENTER);
        }
        setNotes(ppt, slide, "开场：报告题目、本人完成的工作范围，约 20 秒。");
    }

    private void addClosingSlide(XMLSlideShow ppt, String title) {
        XSLFSlide slide = ppt.createSlide();
        paintBackground(slide, new Color(0xF8, 0xFA, 0xFC));
        addTextBox(slide, "总结与致谢", new Rectangle(80, 200, 1120, 60), 32.0, true, new Color(0x1E, 0x29, 0x3B),
            TextParagraph.TextAlign.CENTER);
        List<String> bullets = List.of(
            "完成「" + truncate(title, 28) + "」的设计与实现",
            "通过功能测试验证核心模块可用",
            "后续可在性能、体验与扩展性上继续完善",
            "请各位老师批评指正");
        addBulletBlock(slide, bullets, new Rectangle(280, 300, 720, 280));
        setNotes(ppt, slide, "收束：回顾贡献与不足，感谢导师与评委，预留提问时间。");
    }

    private void addBulletSlide(XMLSlideShow ppt, String title, List<String> bullets, String notes) {
        XSLFSlide slide = ppt.createSlide();
        paintBackground(slide, Color.WHITE);
        addTextBox(slide, title, new Rectangle(60, 36, 1160, 56), 26.0, true, new Color(0x1E, 0x29, 0x3B),
            TextParagraph.TextAlign.LEFT);
        // 顶部分隔线感：一条浅色条
        addTextBox(slide, " ", new Rectangle(60, 96, 200, 4), 4.0, false, new Color(0x4F, 0x46, 0xE5),
            TextParagraph.TextAlign.LEFT);
        addBulletBlock(slide, bullets, new Rectangle(80, 130, 1120, 520));
        setNotes(ppt, slide, notes);
    }

    private void addBulletBlock(XSLFSlide slide, List<String> bullets, Rectangle box) {
        XSLFTextShape shape = slide.createTextBox();
        shape.setAnchor(box);
        shape.clearText();
        for (String bullet : bullets) {
            if (StringUtils.isBlank(bullet)) {
                continue;
            }
            XSLFTextParagraph p = shape.addNewTextParagraph();
            p.setBullet(true);
            p.setLeftMargin(24.0);
            p.setIndent(-18.0);
            p.setSpaceAfter(10.0);
            XSLFTextRun run = p.addNewTextRun();
            run.setText(truncate(bullet.trim(), MAX_BULLET_CHARS));
            run.setFontSize(18.0);
            run.setFontFamily("Microsoft YaHei");
            run.setFontColor(new Color(0x33, 0x41, 0x55));
        }
    }

    private void addTextBox(XSLFSlide slide, String text, Rectangle box, double fontSize, boolean bold,
                            Color color, TextParagraph.TextAlign align) {
        XSLFTextShape shape = slide.createTextBox();
        shape.setAnchor(box);
        shape.clearText();
        XSLFTextParagraph p = shape.addNewTextParagraph();
        p.setTextAlign(align);
        XSLFTextRun run = p.addNewTextRun();
        run.setText(text == null ? "" : text);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setFontFamily("Microsoft YaHei");
        run.setFontColor(color);
    }

    private void paintBackground(XSLFSlide slide, Color color) {
        try {
            slide.getBackground().setFillColor(color);
        } catch (Exception ignored) {
            // 部分 POI 版本背景设置可能受限，忽略即可
        }
    }

    private void setNotes(XMLSlideShow ppt, XSLFSlide slide, String notes) {
        if (StringUtils.isBlank(notes)) {
            return;
        }
        try {
            XSLFNotes notesSlide = ppt.getNotesSlide(slide);
            if (notesSlide == null) {
                return;
            }
            for (XSLFShape shape : notesSlide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    textShape.clearText();
                    XSLFTextParagraph p = textShape.addNewTextParagraph();
                    XSLFTextRun run = p.addNewTextRun();
                    run.setText(notes);
                    run.setFontSize(12.0);
                    run.setFontFamily("Microsoft YaHei");
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("写入 PPT 备注失败: {}", e.getMessage());
        }
    }

    private static String resolveEnvOneLiner(PaperSession session) {
        if (session.getUserInputs() == null || StringUtils.isBlank(session.getUserInputs().getEnvInfo())) {
            return "";
        }
        return truncate(session.getUserInputs().getEnvInfo().replaceAll("\\s+", " "), 60);
    }

    private static List<String> collectLevel1Titles(List<TocNode> toc) {
        List<String> titles = new ArrayList<>();
        if (toc == null) {
            return titles;
        }
        for (TocNode node : toc) {
            if (node == null || StringUtils.isBlank(node.getTitle())) {
                continue;
            }
            if (isSkippedForOutline(node)) {
                continue;
            }
            titles.add(shortTitle(node.getTitle()));
            if (titles.size() >= MAX_BULLETS) {
                break;
            }
        }
        return titles;
    }

    private static List<TocNode> collectContentChapterRoots(List<TocNode> toc) {
        List<TocNode> roots = new ArrayList<>();
        if (toc == null) {
            return roots;
        }
        for (TocNode node : toc) {
            if (node == null || isSkippedForOutline(node)) {
                continue;
            }
            roots.add(node);
        }
        return roots;
    }

    private static boolean isSkippedForOutline(TocNode node) {
        String title = node.getTitle() == null ? "" : node.getTitle();
        String id = node.getId() == null ? "" : node.getId().toLowerCase(Locale.ROOT);
        return title.contains("摘要") || title.contains("致谢") || title.contains("参考文献")
            || id.contains("abstract") || id.contains("ack") || id.contains("reference");
    }

    private static List<TocNode> collectWritableLeaves(TocNode root) {
        List<TocNode> leaves = new ArrayList<>();
        walkLeaves(root, leaves);
        return leaves;
    }

    private static void walkLeaves(TocNode node, List<TocNode> out) {
        if (node == null) {
            return;
        }
        List<TocNode> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            out.add(node);
            return;
        }
        for (TocNode child : children) {
            walkLeaves(child, out);
        }
    }

    private static List<String> extractBullets(String content, int limit) {
        List<String> bullets = new ArrayList<>();
        if (StringUtils.isBlank(content) || limit <= 0) {
            return bullets;
        }
        String cleaned = content
            .replace("\r\n", "\n")
            .replaceAll("(?m)^#{1,6}\\s*", "")
            .replaceAll("【此处插入[^】]*】", "")
            .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")
            .replaceAll("\\[[0-9,，\\-\\s]+\\]", "")
            .replaceAll("[*|`>]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (cleaned.isEmpty()) {
            return bullets;
        }
        String[] sentences = cleaned.split("[。！？；;!?]\\s*");
        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.length() < 8) {
                continue;
            }
            bullets.add(truncate(s, MAX_BULLET_CHARS));
            if (bullets.size() >= limit) {
                break;
            }
        }
        if (bullets.isEmpty() && cleaned.length() >= 8) {
            bullets.add(truncate(cleaned, MAX_BULLET_CHARS));
        }
        return bullets;
    }

    private static String shortTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return "内容要点";
        }
        String t = title.trim()
            .replaceAll("^[一二三四五六七八九十百千零〇两\\d]+[、.．]\\s*", "")
            .replaceAll("^第[一二三四五六七八九十百千零〇两\\d]+章\\s*", "")
            .replaceAll("^\\d+(?:\\.\\d+)*\\s*", "")
            .trim();
        return truncate(StringUtils.isBlank(t) ? title.trim() : t, 28);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(1, max - 1)) + "…";
    }
}
