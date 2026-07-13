package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperSessionSummary;
import org.ruoyi.domain.paper.TocNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 论文生成智能体——会话存储门面（底层持久化到 MySQL）。
 */
@Component
@RequiredArgsConstructor
public class PaperSessionStore {

    private final PaperSessionPersistence persistence;

    /**
     * 创建会话并写入数据库。
     *
     * @param userId 当前登录用户 id
     */
    public PaperSession create(Long userId) {
        return persistence.createSession(userId);
    }

    public PaperSession get(String sessionId) {
        return persistence.getSession(sessionId);
    }

    /**
     * 校验归属并加载会话。
     */
    public PaperSession require(String sessionId, Long userId) {
        return persistence.requireSession(sessionId, userId);
    }

    public void update(String sessionId, Consumer<PaperSession> updater) {
        persistence.updateSession(sessionId, updater);
    }

    public void remove(String sessionId) {
        persistence.removeSession(sessionId);
    }

    public List<PaperSessionSummary> listByUser(Long userId, int limit) {
        return persistence.listByUser(userId, limit);
    }

    /**
     * 保存章节正文（含用户手动编辑）。
     */
    public void saveChapterContent(String sessionId, Long userId, String chapterId, String content) {
        persistence.requireSession(sessionId, userId);
        persistence.updateSession(sessionId, session -> {
            if (content == null) {
                return;
            }
            session.getGeneratedContent().put(chapterId, content);
            TocNode node = findNode(session.getToc(), chapterId);
            if (node != null && StringUtils.isNotBlank(content) && !"generating".equals(node.getStatus())) {
                node.setStatus("done");
                node.setGenerated(true);
            }
            if (StringUtils.isNotBlank(content)
                && (PaperSession.Status.TOC_CONFIRMED.equals(session.getStatus())
                || PaperSession.Status.REF_CONFIRMED.equals(session.getStatus()))) {
                session.setStatus(PaperSession.Status.WRITING);
            }
        });
    }

    private TocNode findNode(List<TocNode> nodes, String chapterId) {
        if (nodes == null || StringUtils.isBlank(chapterId)) {
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
}
