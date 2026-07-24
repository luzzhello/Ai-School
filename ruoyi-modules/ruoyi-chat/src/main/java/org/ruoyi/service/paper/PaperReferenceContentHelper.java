package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.domain.paper.TocNode;

import java.util.List;

/**
 * 参考文献章节内容拼装（第一步确认后的文献直接用于正文，不再走 AI 生成）。
 */
public final class PaperReferenceContentHelper {

    private PaperReferenceContentHelper() {
    }

    public static boolean isReferenceChapter(String chapterId, TocNode node) {
        if (StringUtils.isNotBlank(chapterId)) {
            String id = chapterId.toLowerCase();
            if (id.contains("reference") || "references".equals(id)) {
                return true;
            }
        }
        if (node != null && StringUtils.isNotBlank(node.getTitle())) {
            return node.getTitle().contains("参考文献");
        }
        return false;
    }

    public static String findReferenceChapterId(List<TocNode> toc) {
        if (toc == null) {
            return null;
        }
        for (TocNode node : toc) {
            if (isReferenceChapter(node.getId(), node)) {
                return node.getId();
            }
            String childId = findReferenceChapterId(node.getChildren());
            if (childId != null) {
                return childId;
            }
        }
        return null;
    }

    /**
     * 将已确认参考文献格式化为参考文献章节正文。
     */
    public static String formatChapterContent(List<Reference> references) {
        if (references == null || references.isEmpty()) {
            return "（暂无参考文献，请返回第一步检索并确认文献）";
        }
        StringBuilder sb = new StringBuilder();
        for (Reference ref : references) {
            int index = ref.getIndex() == null ? 0 : ref.getIndex();
            sb.append('[').append(index).append("] ");
            if (StringUtils.isNotBlank(ref.getCitation())) {
                sb.append(ref.getCitation().trim());
            } else {
                sb.append(buildFallbackCitation(ref));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 把已确认文献写入会话的参考文献章节，并标记目录节点为 done。
     */
    public static void syncReferenceChapter(PaperSession session) {
        if (session == null || session.getToc() == null || session.getToc().isEmpty()) {
            return;
        }
        String chapterId = findReferenceChapterId(session.getToc());
        if (chapterId == null) {
            return;
        }
        String content = formatChapterContent(session.getReferences());
        session.getGeneratedContent().put(chapterId, content);
        TocNode node = findNode(session.getToc(), chapterId);
        if (node != null) {
            node.setStatus("done");
            node.setGenerated(true);
        }
    }

    private static TocNode findNode(List<TocNode> nodes, String chapterId) {
        if (nodes == null) {
            return null;
        }
        for (TocNode node : nodes) {
            if (chapterId.equals(node.getId())) {
                return node;
            }
            TocNode found = findNode(node.getChildren(), chapterId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String buildFallbackCitation(Reference ref) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(ref.getAuthor())) {
            sb.append(ref.getAuthor().trim()).append('.');
        }
        if (StringUtils.isNotBlank(ref.getTitle())) {
            sb.append(ref.getTitle().trim());
        }
        if (StringUtils.isNotBlank(ref.getType())) {
            sb.append('[').append(ref.getType().trim()).append(']');
        }
        if (StringUtils.isNotBlank(ref.getSource())) {
            sb.append('.').append(ref.getSource().trim());
        }
        if (ref.getYear() != null) {
            sb.append(',').append(ref.getYear());
        }
        if (StringUtils.isNotBlank(ref.getDoi())) {
            sb.append('.').append(ref.getDoi().trim());
        }
        return sb.toString();
    }
}
