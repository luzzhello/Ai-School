package org.ruoyi.service.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.dto.request.PaperReferencesRequest;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.Reference;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 论文生成——参考文献检索（流式 SSE）。
 * <p>
 * 从 {@code lit_paper} 文献库检索真实元数据，不再调用大模型编造文献。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperReferenceService {

    private final LitPaperSearchService litPaperSearchService;
    private final PaperSessionStore paperSessionStore;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "paper-reference");
        t.setDaemon(true);
        return t;
    });

    /**
     * 检索参考文献并通过 SSE 流式推送。
     */
    public void generate(PaperReferencesRequest request, SseEmitter emitter) {
        PaperSession session = paperSessionStore.get(request.getSessionId());
        if (session == null) {
            sendError(emitter, "会话不存在或已过期");
            return;
        }
        String keyword = StringUtils.isNotBlank(request.getKeyword())
            ? request.getKeyword().trim()
            : session.getTitle();
        if (StringUtils.isBlank(keyword)) {
            sendError(emitter, "请先填写论文题目或检索关键词");
            return;
        }
        int count = request.getCount() != null && request.getCount() > 0
            ? Math.min(request.getCount(), 50)
            : 20;
        String language = StringUtils.isBlank(request.getLanguage()) ? null : request.getLanguage().trim().toLowerCase();
        executor.submit(() -> runSearch(request.getSessionId(), keyword, language, count, emitter));
    }

    private void runSearch(String sessionId, String keyword, String language, int count, SseEmitter emitter) {
        try {
            sendEvent(emitter, Map.of("type", "start", "title", keyword));
            log.info("文献库检索参考文献, sessionId={}, keyword={}, count={}, language={}",
                sessionId, keyword, count, language);

            List<Reference> references = litPaperSearchService.search(keyword, language, count);
            if (references.isEmpty()) {
                sendError(emitter, "文献库未匹配到相关文献，请更换关键词或先导入文献");
                return;
            }

            paperSessionStore.update(sessionId, s -> s.setReferences(references));

            for (Reference ref : references) {
                sendEvent(emitter, Map.of("type", "reference", "reference", ref));
            }
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("type", "done");
            done.put("total", references.size());
            if (references.size() < count) {
                done.put("message", "文献库匹配到 " + references.size() + " 篇");
            }
            sendEvent(emitter, done);
            emitter.complete();
        } catch (ServiceException e) {
            sendError(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("检索参考文献失败, sessionId={}", sessionId, e);
            sendError(emitter, "检索参考文献失败，请稍后重试");
        }
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            throw new ServiceException("推送事件失败");
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "error");
            payload.put("content", message);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
