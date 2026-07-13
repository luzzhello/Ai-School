package org.ruoyi.service.paper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.entity.paper.PaperChapterEntity;
import org.ruoyi.domain.entity.paper.PaperReferenceEntity;
import org.ruoyi.domain.entity.paper.PaperSessionEntity;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperSessionSummary;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.domain.paper.TocNode;
import org.ruoyi.mapper.paper.PaperChapterMapper;
import org.ruoyi.mapper.paper.PaperReferenceMapper;
import org.ruoyi.mapper.paper.PaperSessionMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 论文生成智能体——数据库持久化（会话 / 参考文献 / 章节内容）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperSessionPersistence {

    private static final TypeReference<List<TocNode>> TOC_TYPE = new TypeReference<>() {};
    private static final TypeReference<PaperSession.SqlParsed> SQL_PARSED_TYPE = new TypeReference<>() {};

    private final PaperSessionMapper sessionMapper;
    private final PaperReferenceMapper referenceMapper;
    private final PaperChapterMapper chapterMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建会话并写入数据库。
     */
    public PaperSession createSession(Long userId) {
        if (userId == null) {
            throw new ServiceException("请先登录");
        }
        Date now = new Date();
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        PaperSessionEntity entity = new PaperSessionEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setStatus(PaperSession.Status.INIT);
        entity.setWordCount(15000);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        sessionMapper.insert(entity);

        PaperSession session = new PaperSession();
        session.setSessionId(sessionId);
        session.setStatus(PaperSession.Status.INIT);
        session.getUserInputs().setWordCount(15000);
        session.setCreateTime(now.getTime());
        session.setUpdateTime(now.getTime());
        return session;
    }

    /**
     * 按 sessionId 加载完整会话（含文献、章节、大纲状态）。
     */
    public PaperSession getSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return null;
        }
        PaperSessionEntity entity = findEntity(sessionId);
        if (entity == null) {
            return null;
        }
        return toDomain(entity);
    }

    /**
     * 校验会话存在且归属当前用户。
     */
    public PaperSession requireSession(String sessionId, Long userId) {
        PaperSessionEntity entity = findEntity(sessionId);
        if (entity == null) {
            throw new ServiceException("会话不存在或已过期");
        }
        if (userId != null && !entity.getUserId().equals(userId)) {
            throw new ServiceException("无权访问该会话");
        }
        return toDomain(entity);
    }

    /**
     * 原子更新：加载 → 修改 → 落库。
     */
    public void updateSession(String sessionId, Consumer<PaperSession> updater) {
        if (sessionId == null || updater == null) {
            return;
        }
        PaperSession session = getSession(sessionId);
        if (session == null) {
            return;
        }
        updater.accept(session);
        session.setUpdateTime(System.currentTimeMillis());
        saveSession(session);
    }

    /**
     * 按用户查询最近会话列表（按更新时间倒序）。
     */
    public List<PaperSessionSummary> listByUser(Long userId, int limit) {
        if (userId == null) {
            throw new ServiceException("请先登录");
        }
        int cap = limit <= 0 ? 50 : Math.min(limit, 100);
        List<PaperSessionEntity> rows = sessionMapper.selectList(
            Wrappers.<PaperSessionEntity>lambdaQuery()
                .eq(PaperSessionEntity::getUserId, userId)
                .orderByDesc(PaperSessionEntity::getUpdateTime)
                .last("LIMIT " + cap));

        List<PaperSessionSummary> result = new ArrayList<>();
        for (PaperSessionEntity entity : rows) {
            PaperSessionSummary summary = new PaperSessionSummary();
            summary.setSessionId(entity.getSessionId());
            summary.setTitle(StringUtils.isNotBlank(entity.getTitle()) ? entity.getTitle() : "未命名论文");
            summary.setStatus(entity.getStatus() == null ? PaperSession.Status.INIT : entity.getStatus());
            if (entity.getCreateTime() != null) {
                summary.setCreateTime(entity.getCreateTime().getTime());
            }
            if (entity.getUpdateTime() != null) {
                summary.setUpdateTime(entity.getUpdateTime().getTime());
            }
            fillChapterProgress(summary, entity);
            result.add(summary);
        }
        return result;
    }

    /**
     * 删除会话及关联数据。
     */
    public void removeSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        referenceMapper.delete(Wrappers.<PaperReferenceEntity>lambdaQuery()
            .eq(PaperReferenceEntity::getSessionId, sessionId));
        chapterMapper.delete(Wrappers.<PaperChapterEntity>lambdaQuery()
            .eq(PaperChapterEntity::getSessionId, sessionId));
        sessionMapper.delete(Wrappers.<PaperSessionEntity>lambdaQuery()
            .eq(PaperSessionEntity::getSessionId, sessionId));
    }

    // ---------------- 内部：组装 / 落库 ----------------

    private PaperSession toDomain(PaperSessionEntity entity) {
        PaperSession session = new PaperSession();
        session.setSessionId(entity.getSessionId());
        session.setTitle(entity.getTitle());
        session.setStatus(entity.getStatus() == null ? PaperSession.Status.INIT : entity.getStatus());

        PaperSession.UserInputs inputs = session.getUserInputs();
        inputs.setSqlContent(entity.getSqlContent());
        inputs.setCodeContent(entity.getCodeContent());
        inputs.setEnvInfo(entity.getEnvInfo());
        inputs.setWordCount(entity.getWordCount() == null ? 15000 : entity.getWordCount());
        inputs.setEducationLevel(entity.getEducationLevel());

        session.setSqlParsed(parseJson(entity.getSqlParsedJson(), SQL_PARSED_TYPE, new PaperSession.SqlParsed()));
        session.setToc(parseJson(entity.getTocJson(), TOC_TYPE, new ArrayList<>()));
        session.setReferences(loadReferences(entity.getSessionId()));

        List<PaperChapterEntity> chapterRows = chapterMapper.selectList(
            Wrappers.<PaperChapterEntity>lambdaQuery()
                .eq(PaperChapterEntity::getSessionId, entity.getSessionId()));
        Map<String, String> contentMap = new LinkedHashMap<>();
        Map<String, String> statusMap = new LinkedHashMap<>();
        for (PaperChapterEntity row : chapterRows) {
            if (StringUtils.isNotBlank(row.getContent())) {
                contentMap.put(row.getChapterId(), row.getContent());
            }
            if (StringUtils.isNotBlank(row.getStatus())) {
                statusMap.put(row.getChapterId(), row.getStatus());
            }
        }
        session.setGeneratedContent(contentMap);
        syncTocFromChapters(session.getToc(), contentMap, statusMap);

        if (entity.getCreateTime() != null) {
            session.setCreateTime(entity.getCreateTime().getTime());
        }
        if (entity.getUpdateTime() != null) {
            session.setUpdateTime(entity.getUpdateTime().getTime());
        }
        return session;
    }

    private void saveSession(PaperSession session) {
        PaperSessionEntity entity = findEntity(session.getSessionId());
        if (entity == null) {
            throw new ServiceException("会话不存在");
        }
        Date now = new Date();
        entity.setTitle(session.getTitle());
        entity.setStatus(session.getStatus());
        entity.setWordCount(session.getUserInputs().getWordCount());
        entity.setEducationLevel(session.getUserInputs().getEducationLevel());
        entity.setEnvInfo(session.getUserInputs().getEnvInfo());
        entity.setSqlContent(session.getUserInputs().getSqlContent());
        entity.setCodeContent(session.getUserInputs().getCodeContent());
        entity.setSqlParsedJson(toJson(session.getSqlParsed()));
        entity.setTocJson(toJson(session.getToc()));
        entity.setUpdateTime(now);
        sessionMapper.updateById(entity);

        saveReferences(session.getSessionId(), session.getReferences(), now);
        saveChapters(session.getSessionId(), session.getToc(), session.getGeneratedContent(), now);
    }

    private List<Reference> loadReferences(String sessionId) {
        List<PaperReferenceEntity> rows = referenceMapper.selectList(
            Wrappers.<PaperReferenceEntity>lambdaQuery()
                .eq(PaperReferenceEntity::getSessionId, sessionId)
                .orderByAsc(PaperReferenceEntity::getRefIndex));
        List<Reference> list = new ArrayList<>();
        for (PaperReferenceEntity row : rows) {
            Reference ref = new Reference();
            ref.setIndex(row.getRefIndex());
            ref.setAuthor(row.getAuthor());
            ref.setTitle(row.getTitle());
            ref.setSource(row.getSource());
            ref.setYear(row.getYear());
            ref.setDoi(row.getDoi());
            ref.setType(row.getType());
            ref.setCitation(row.getCitation());
            ref.setLanguage(row.getLanguage());
            ref.setChapter(row.getChapter());
            list.add(ref);
        }
        return list;
    }

    private void syncTocFromChapters(List<TocNode> nodes, Map<String, String> contentMap,
                                     Map<String, String> statusMap) {
        if (nodes == null) {
            return;
        }
        for (TocNode node : nodes) {
            String dbStatus = statusMap.get(node.getId());
            if (StringUtils.isNotBlank(dbStatus)) {
                node.setStatus(dbStatus);
                node.setGenerated("done".equals(dbStatus));
            } else if (contentMap.containsKey(node.getId()) && StringUtils.isNotBlank(contentMap.get(node.getId()))) {
                if (!"generating".equals(node.getStatus())) {
                    node.setStatus("done");
                    node.setGenerated(true);
                }
            }
            if (node.getChildren() != null) {
                syncTocFromChapters(node.getChildren(), contentMap, statusMap);
            }
        }
    }

    private void saveReferences(String sessionId, List<Reference> references, Date now) {
        referenceMapper.delete(Wrappers.<PaperReferenceEntity>lambdaQuery()
            .eq(PaperReferenceEntity::getSessionId, sessionId));
        if (references == null || references.isEmpty()) {
            return;
        }
        int idx = 1;
        for (Reference ref : references) {
            PaperReferenceEntity row = new PaperReferenceEntity();
            row.setSessionId(sessionId);
            row.setRefIndex(ref.getIndex() != null ? ref.getIndex() : idx);
            row.setAuthor(ref.getAuthor());
            row.setTitle(ref.getTitle());
            row.setSource(ref.getSource());
            row.setYear(ref.getYear());
            row.setDoi(ref.getDoi());
            row.setType(ref.getType());
            row.setCitation(ref.getCitation());
            row.setLanguage(ref.getLanguage());
            row.setChapter(ref.getChapter());
            row.setCreateTime(now);
            row.setUpdateTime(now);
            referenceMapper.insert(row);
            idx++;
        }
    }

    private void saveChapters(String sessionId, List<TocNode> toc, Map<String, String> contentMap, Date now) {
        Map<String, TocNode> nodeMap = flattenToc(toc);
        if (contentMap != null) {
            for (Map.Entry<String, String> entry : contentMap.entrySet()) {
                TocNode node = nodeMap.get(entry.getKey());
                upsertChapter(sessionId, entry.getKey(),
                    node != null ? node.getTitle() : null,
                    node != null ? node.getStatus() : "done",
                    entry.getValue(), now);
            }
        }
        if (toc != null) {
            for (Map.Entry<String, TocNode> entry : nodeMap.entrySet()) {
                if (contentMap != null && contentMap.containsKey(entry.getKey())) {
                    continue;
                }
                TocNode node = entry.getValue();
                upsertChapter(sessionId, entry.getKey(), node.getTitle(), node.getStatus(), null, now);
            }
        }
    }

    private void upsertChapter(String sessionId, String chapterId, String title, String status,
                               String content, Date now) {
        PaperChapterEntity existing = chapterMapper.selectOne(
            Wrappers.<PaperChapterEntity>lambdaQuery()
                .eq(PaperChapterEntity::getSessionId, sessionId)
                .eq(PaperChapterEntity::getChapterId, chapterId));
        if (existing == null) {
            PaperChapterEntity row = new PaperChapterEntity();
            row.setSessionId(sessionId);
            row.setChapterId(chapterId);
            row.setChapterTitle(title);
            row.setStatus(StringUtils.isBlank(status) ? "pending" : status);
            row.setContent(content);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            chapterMapper.insert(row);
            return;
        }
        existing.setChapterTitle(title);
        if (StringUtils.isNotBlank(status)) {
            existing.setStatus(status);
        }
        if (content != null) {
            existing.setContent(content);
        }
        existing.setUpdateTime(now);
        chapterMapper.updateById(existing);
    }

    private void fillChapterProgress(PaperSessionSummary summary, PaperSessionEntity entity) {
        List<PaperChapterEntity> chapters = chapterMapper.selectList(
            Wrappers.<PaperChapterEntity>lambdaQuery()
                .eq(PaperChapterEntity::getSessionId, entity.getSessionId()));

        int total = chapters.size();
        int done = 0;
        for (PaperChapterEntity chapter : chapters) {
            if ("done".equals(chapter.getStatus()) || StringUtils.isNotBlank(chapter.getContent())) {
                done++;
            }
        }
        if (total == 0 && StringUtils.isNotBlank(entity.getTocJson())) {
            List<TocNode> toc = parseJson(entity.getTocJson(), TOC_TYPE, new ArrayList<>());
            total = flattenToc(toc).size();
        }
        summary.setChapterTotal(total);
        summary.setChapterDone(done);
    }

    private Map<String, TocNode> flattenToc(List<TocNode> nodes) {
        Map<String, TocNode> map = new LinkedHashMap<>();
        flattenTocRecursive(nodes, map);
        return map;
    }

    private void flattenTocRecursive(List<TocNode> nodes, Map<String, TocNode> map) {
        if (nodes == null) {
            return;
        }
        for (TocNode node : nodes) {
            map.put(node.getId(), node);
            flattenTocRecursive(node.getChildren(), map);
        }
    }

    private PaperSessionEntity findEntity(String sessionId) {
        return sessionMapper.selectOne(Wrappers.<PaperSessionEntity>lambdaQuery()
            .eq(PaperSessionEntity::getSessionId, sessionId));
    }

    private <T> T parseJson(String json, TypeReference<T> type, T fallback) {
        if (StringUtils.isBlank(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("解析 JSON 失败: {}", e.getMessage());
            return fallback;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("序列化 JSON 失败: {}", e.getMessage());
            return null;
        }
    }
}
