package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperUiScreenshot;
import org.ruoyi.domain.paper.TocNode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 论文生成智能体——目录大纲生成服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTocService {

    private final PaperSessionStore paperSessionStore;
    private final PaperTemplateTocLoader templateTocLoader;
    private final PaperTocCustomizer paperTocCustomizer;

    /**
     * 生成目录大纲并存入会话。
     *
     * @param sessionId           会话 id
     * @param model               预留（当前不使用 AI 生成大纲）
     * @param useDefaultTemplate  是否使用论文模板 docx 默认大纲
     */
    public List<TocNode> generate(String sessionId, String model, boolean useDefaultTemplate) {
        PaperSession session = paperSessionStore.get(sessionId);
        if (session == null) {
            throw new ServiceException("会话不存在或已过期");
        }
        if (StringUtils.isBlank(session.getTitle())) {
            throw new ServiceException("请先填写论文题目");
        }
        requireUiScreenshots(session);

        List<String> tables = session.getSqlParsed() == null ? List.of() : session.getSqlParsed().getTables();
        List<TocNode> toc = useDefaultTemplate
            ? templateTocLoader.load(tables)
            : PaperStandardTocTemplate.build(tables);
        paperTocCustomizer.customize(toc, session);
        applyWordLimits(toc, session);

        log.info("生成论文目录, sessionId={}, title={}, defaultTemplate={}, nodes={}",
            sessionId, session.getTitle(), useDefaultTemplate, toc.size());

        paperSessionStore.update(sessionId, s -> {
            s.setToc(toc);
            s.setStatus(PaperSession.Status.TOC_CONFIRMED);
            PaperReferenceContentHelper.syncReferenceChapter(s);
        });
        return toc;
    }

    /**
     * 根据最新 SQL 解析结果刷新第五章模块（会话已有大纲时调用）。
     */
    public List<TocNode> refreshChapter5(String sessionId) {
        PaperSession session = paperSessionStore.get(sessionId);
        if (session == null) {
            throw new ServiceException("会话不存在或已过期");
        }
        List<TocNode> toc = session.getToc();
        if (toc == null || toc.isEmpty()) {
            return List.of();
        }
        requireUiScreenshots(session);
        paperTocCustomizer.refreshChapter5Modules(toc, session);
        PaperTocNodeIds.assign(toc, "");
        applyWordLimits(toc, session);
        paperSessionStore.update(sessionId, s -> s.setToc(toc));
        log.info("第五章模块已按 SQL 刷新, sessionId={}", sessionId);
        return toc;
    }

    private void applyWordLimits(List<TocNode> toc, PaperSession session) {
        Integer target = session.getUserInputs() == null ? null : session.getUserInputs().getWordCount();
        PaperWordLimitAllocator.apply(toc, target);
    }

    /**
     * 第五章「系统实现」完全由功能界面截图驱动，生成/刷新前必须至少上传一侧（管理员或用户）截图。
     */
    private void requireUiScreenshots(PaperSession session) {
        List<PaperUiScreenshot> shots = session.getUiScreenshots();
        if (shots == null || shots.isEmpty()) {
            throw new ServiceException("请先上传系统功能截图（管理员或用户至少一侧）");
        }
        boolean hasImage = shots.stream().anyMatch(s -> {
            if (s == null) {
                return false;
            }
            if (s.getImages() != null) {
                return s.getImages().stream().anyMatch(
                    img -> img != null && StringUtils.isNotBlank(img.getAssetUrl()));
            }
            return StringUtils.isNotBlank(s.getAssetUrl());
        });
        if (!hasImage) {
            throw new ServiceException("请先上传系统功能截图（管理员或用户至少一侧）");
        }
    }
}
