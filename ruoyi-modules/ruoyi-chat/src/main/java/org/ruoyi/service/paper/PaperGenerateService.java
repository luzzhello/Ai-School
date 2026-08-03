package org.ruoyi.service.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.ErDiagramProperties;
import org.ruoyi.domain.paper.PaperUiScreenshotImage;
import org.ruoyi.domain.paper.SqlColumnInfo;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.domain.paper.TocNode;
import org.ruoyi.service.draw.impl.DrawChatModelSupport;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 论文生成智能体——逐章流式生成服务。
 * <p>
 * 用户点击目录树某节，注入「全局 Prompt + 参考文献 + SQL 摘要 + 大纲 + 章节指令」，
 * 调用流式模型实时推送内容；完成后写入会话已生成内容。对应 PRD「3.4 / 4.x」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperGenerateService {

    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(180);

    /** 全局系统 Prompt（所有章节通用） */
    private static final String SYSTEM_PROMPT = PaperWritingStandards.SYSTEM_ROLE;

    /** 英文 ABSTRACT 专用系统 Prompt（与论文章节 Prompt 分离，减少干扰） */
    private static final String ENGLISH_ABSTRACT_SYSTEM_PROMPT = """
        You are a professional academic English translator for computer science thesis abstracts.
        Line 1 MUST be the English translation of the Chinese paper title (never leave it in Chinese).
        Then write the abstract body (250-300 words) and a Keywords: line.
        Do not output an Abstract / ABSTRACT heading line. Do not output Chinese.
        Keep each abstract paragraph as continuous text without hard line breaks mid-paragraph.
        """;

    /** 占位提示（生成过程中写入正文，完成后会被完整内容替换） */
    private static final String ENGLISH_PENDING_HINT = "（正在生成英文 ABSTRACT，请稍候…）";

    /** 注入 Prompt 的代码摘要最大长度 */
    private final IChatModelService chatModelService;
    private final ErDiagramProperties erDiagramProperties;
    private final PaperSessionStore paperSessionStore;
    private final ObjectMapper objectMapper;

    /**
     * 参考文献已在第一步确认，直接回传已格式化正文，不走 AI。
     */
    private void deliverReferenceChapter(String sessionId, String chapterId, PaperSession session, SseEmitter emitter) {
        String content = PaperReferenceContentHelper.formatChapterContent(session.getReferences());
        paperSessionStore.update(sessionId, s -> PaperReferenceContentHelper.syncReferenceChapter(s));
        sendEvent(emitter, Map.of("type", "start", "chapterId", chapterId));
        sendEvent(emitter, Map.of("type", "done", "chapterId", chapterId, "content", content));
        emitter.complete();
    }

    /**
     * 摘要：同步生成中文 + 英文 ABSTRACT（同一线程推送 SSE，避免异步丢失英文段）。
     */
    private void generateAbstractChapter(String sessionId, String chapterId, PaperSession session,
                                         String model, SseEmitter emitter) {
        String modelName = resolveModelName(model);
        sendEvent(emitter, Map.of("type", "start", "chapterId", chapterId));
        markChapterStatus(sessionId, chapterId, "generating");
        log.info("AI 生成摘要（中+英同步）, sessionId={}, chapterId={}", sessionId, chapterId);
        generateAbstractChapterSync(sessionId, chapterId, session, modelName, emitter);
    }

    private void generateAbstractChapterSync(String sessionId, String chapterId, PaperSession session,
                                             String modelName, SseEmitter emitter) {
        String chinese = null;
        try {
            String existing = session.getGeneratedContent() == null ? null
                : session.getGeneratedContent().get(chapterId);
            if (StringUtils.isNotBlank(existing) && !PaperChapterPrompts.hasEnglishAbstract(existing)) {
                chinese = extractChinesePart(existing);
                log.info("检测到已有中文摘要，仅补生成英文 ABSTRACT, sessionId={}", sessionId);
                finalizeAbstractWithEnglish(sessionId, chapterId, session, modelName, emitter, chinese);
                return;
            }

            ChatModel chatModel = DrawChatModelSupport.buildModel(chatModelService, modelName);
            String chinesePrompt = buildAbstractChinesePrompt(session);

            log.info("开始生成中文摘要, sessionId={}", sessionId);
            chinese = DrawChatModelSupport.chat(chatModel, SYSTEM_PROMPT, chinesePrompt);
            if (StringUtils.isBlank(chinese)) {
                throw new ServiceException("中文摘要生成为空");
            }
            chinese = chinese.trim();
            sendEvent(emitter, Map.of("type", "content", "chapterId", chapterId, "delta", chinese));
            finalizeAbstractWithEnglish(sessionId, chapterId, session, modelName, emitter, chinese);
        } catch (ServiceException e) {
            log.error("摘要生成失败, sessionId={}", sessionId, e);
            savePartialChinese(sessionId, chapterId, chinese);
            markChapterStatus(sessionId, chapterId, "pending");
            sendError(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("摘要生成失败, sessionId={}", sessionId, e);
            savePartialChinese(sessionId, chapterId, chinese);
            markChapterStatus(sessionId, chapterId, "pending");
            sendError(emitter, "摘要生成失败，请稍后重试");
        }
    }

    private void finalizeAbstractWithEnglish(String sessionId, String chapterId, PaperSession session,
                                             String modelName, SseEmitter emitter, String chinese) {
        sendEvent(emitter, Map.of(
            "type", "content",
            "chapterId", chapterId,
            "delta", "\n\n" + ENGLISH_PENDING_HINT + "\n\n"
        ));

        ChatModel chatModel = DrawChatModelSupport.buildModel(chatModelService, modelName);
        String title = session.getTitle() == null ? "" : session.getTitle();
        log.info("开始生成英文 ABSTRACT, sessionId={}", sessionId);
        String englishPart = translateEnglishAbstract(chatModel, chinese, title);
        englishPart = normalizeEnglishAbstract(englishPart.trim());

        String full = chinese + "\n\n" + englishPart;
        sendEvent(emitter, Map.of("type", "content", "chapterId", chapterId, "delta", "\n\n" + englishPart));
        log.info("摘要生成完成（含英文）, sessionId={}, length={}", sessionId, full.length());
        finalizeChapter(sessionId, chapterId, full, emitter);
    }

    private String translateEnglishAbstract(ChatModel chatModel, String chinese, String title) {
        String prompt = PaperChapterPrompts.promptAbstractEnglishFromChinese(
            truncateForTranslation(chinese), title);
        ServiceException lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String result = DrawChatModelSupport.chat(
                    chatModel, ENGLISH_ABSTRACT_SYSTEM_PROMPT, prompt);
                if (StringUtils.isNotBlank(result)) {
                    return result;
                }
            } catch (ServiceException e) {
                lastError = e;
                log.warn("英文 ABSTRACT 第{}次生成失败, sessionId-less retry", attempt);
            }
        }
        throw lastError != null ? lastError : new ServiceException("英文摘要生成失败，请重试");
    }

    private String truncateForTranslation(String chinese) {
        if (chinese == null || chinese.length() <= 3500) {
            return chinese;
        }
        return chinese.substring(0, 3500);
    }

    private String extractChinesePart(String content) {
        if (StringUtils.isBlank(content)) {
            return content;
        }
        String cleaned = content.replace(ENGLISH_PENDING_HINT, "").trim();
        int splitIdx = indexOfEnglishAbstractStart(cleaned);
        if (splitIdx > 0) {
            String before = cleaned.substring(0, splitIdx).trim();
            java.util.regex.Matcher kw = java.util.regex.Pattern
                .compile("(?m)^关键词[：:]")
                .matcher(before);
            if (kw.find()) {
                int kwLineEnd = before.indexOf('\n', kw.start());
                if (kwLineEnd >= 0) {
                    return before.substring(0, kwLineEnd + 1).trim();
                }
            }
            return before;
        }
        java.util.regex.Matcher kwOnly = java.util.regex.Pattern.compile("(?m)^关键词[：:]").matcher(cleaned);
        if (kwOnly.find()) {
            int kwLineEnd = cleaned.indexOf('\n', kwOnly.start());
            if (kwLineEnd >= 0) {
                return cleaned.substring(0, kwLineEnd + 1).trim();
            }
        }
        return cleaned;
    }

    private int indexOfEnglishAbstractStart(String text) {
        java.util.regex.Matcher abstractLine = java.util.regex.Pattern.compile("(?im)^ABSTRACT\\s*$").matcher(text);
        if (abstractLine.find()) {
            return abstractLine.start();
        }
        java.util.regex.Matcher abstractHeader = java.util.regex.Pattern.compile("(?im)^Abstract:?\\s*$").matcher(text);
        if (abstractHeader.find()) {
            return abstractHeader.start();
        }
        java.util.regex.Matcher kw = java.util.regex.Pattern.compile("(?m)^关键词[：:]").matcher(text);
        if (!kw.find()) {
            return -1;
        }
        int pos = kw.start();
        int lineEnd = text.indexOf('\n', pos);
        pos = lineEnd >= 0 ? lineEnd + 1 : text.length();
        while (pos < text.length()) {
            int nextEnd = text.indexOf('\n', pos);
            String line = (nextEnd >= 0 ? text.substring(pos, nextEnd) : text.substring(pos)).strip();
            if (line.isEmpty()) {
                pos = nextEnd >= 0 ? nextEnd + 1 : text.length();
                continue;
            }
            long latin = line.chars().filter(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')).count();
            long letters = line.chars().filter(Character::isLetter).count();
            if (letters > 0 && latin * 100 / letters >= 60 && !line.matches("(?i).*Keywords\\s*:.*")) {
                return pos;
            }
            break;
        }
        return -1;
    }

    private int indexOfAbstractHeader(String text) {
        return indexOfEnglishAbstractStart(text);
    }

    private void savePartialChinese(String sessionId, String chapterId, String chinese) {
        if (StringUtils.isBlank(chinese)) {
            return;
        }
        final String partial = chinese.trim();
        paperSessionStore.update(sessionId, s -> s.getGeneratedContent().put(chapterId, partial));
    }

    private String normalizeEnglishAbstract(String englishPart) {
        String normalized = englishPart == null ? "" : englishPart.trim();
        normalized = normalized.replaceAll("(?im)^ABSTRACT\\s*\\n+", "");
        normalized = normalized.replaceAll("(?im)^Abstract:?\\s*\\n+", "");
        if (!normalized.matches("(?si).*Keywords\\s*:.*")) {
            normalized = normalized + "\n\nKeywords: (refer to Chinese keywords)";
        }
        return normalized.trim();
    }

    private String buildAbstractChinesePrompt(PaperSession session) {
        TocNode node = findNode(session.getToc(), "abstract");
        PaperChapterPrompts.PromptContext ctx = buildPromptContext(session, session.getSqlParsed(), node);
        return appendCommonContext(PaperChapterPrompts.promptAbstractChineseOnly(ctx), session, node);
    }

    private void finalizeChapter(String sessionId, String chapterId, String content, SseEmitter emitter) {
        PaperSession session = paperSessionStore.get(sessionId);
        TocNode chapterNode = session == null ? null : findNode(session.getToc(), chapterId);
        String chapterTitle = chapterNode != null ? chapterNode.getTitle() : null;
        if (!PaperChapterPrompts.isAbstractChapter(chapterId, chapterNode)
            && !PaperReferenceContentHelper.isReferenceChapter(chapterId, chapterNode)) {
            content = PaperChapterContentSanitizer.stripDuplicateSectionHeading(content, chapterTitle);
        }
        if (PaperChapterPrompts.isAcknowledgementChapter(chapterId, chapterNode)) {
            content = PaperChapterContentSanitizer.sanitizeAcknowledgementPlaceholders(content);
        }
        // 引用角标后质检：摘要/致谢/设计·实现·测试去掉数字角标；参考文献列表节保留；其余节仅保留已确认序号
        List<TocNode> toc = session == null ? null : session.getToc();
        if (PaperChapterPrompts.isAbstractChapter(chapterId, chapterNode)
            || PaperChapterPrompts.isAcknowledgementChapter(chapterId, chapterNode)
            || PaperChapterPrompts.isNoCitationChapter(chapterId, chapterNode, toc)) {
            content = PaperCitationSanitizer.stripAllNumericCitations(content);
        } else if (!PaperReferenceContentHelper.isReferenceChapter(chapterId, chapterNode)) {
            content = PaperCitationSanitizer.sanitizeToValidIndexes(
                content, PaperCitationSanitizer.collectValidIndexes(
                    session == null ? null : session.getReferences()));
        }
        if (chapterNode != null) {
            List<PaperUiScreenshotImage> images = chapterNode.getScreenshotImages();
            if (images != null && !images.isEmpty()) {
                String bare = extractBareTitle(chapterTitle);
                int chapterNo = PaperUiScreenshotInjector.extractChapterNo(chapterTitle);
                int startFig = PaperUiScreenshotInjector.nextFigureIndex(
                    chapterNo,
                    session == null ? null : session.getGeneratedContent(),
                    content);
                content = PaperUiScreenshotInjector.injectAll(
                    content, images, bare, chapterNo, startFig);
            } else if (StringUtils.isNotBlank(chapterNode.getScreenshotAssetUrl())) {
                String bare = extractBareTitle(chapterTitle);
                int chapterNo = PaperUiScreenshotInjector.extractChapterNo(chapterTitle);
                int startFig = PaperUiScreenshotInjector.nextFigureIndex(
                    chapterNo,
                    session == null ? null : session.getGeneratedContent(),
                    content);
                content = PaperUiScreenshotInjector.injectAll(
                    content,
                    List.of(legacyScreenshotImage(chapterNode.getScreenshotAssetUrl())),
                    bare,
                    chapterNo,
                    startFig);
            }
        }
        final String finalContent = content;
        paperSessionStore.update(sessionId, s -> {
            s.getGeneratedContent().put(chapterId, finalContent);
            if (PaperSession.Status.TOC_CONFIRMED.equals(s.getStatus())
                || PaperSession.Status.REF_CONFIRMED.equals(s.getStatus())
                || PaperSession.Status.INIT.equals(s.getStatus())) {
                s.setStatus(PaperSession.Status.WRITING);
            }
            TocNode node = findNode(s.getToc(), chapterId);
            if (node != null) {
                node.setStatus("done");
                node.setGenerated(true);
            }
        });
        sendEvent(emitter, Map.of("type", "done", "chapterId", chapterId, "content", finalContent));
        emitter.complete();
    }

    /**
     * 去除标题前导的章节编号（如「5.1.1 」「5.1.1」），仅保留可读功能名，供图题使用。
     */
    private String extractBareTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return title;
        }
        return title.trim().replaceFirst("^[0-9]+(?:\\.[0-9]+)*\\s*", "").trim();
    }

    private static PaperUiScreenshotImage legacyScreenshotImage(String assetUrl) {
        PaperUiScreenshotImage image = new PaperUiScreenshotImage();
        image.setAssetUrl(assetUrl);
        image.setLabel(null);
        return image;
    }

    /**
     * 生成指定章节并通过 SSE 流式推送。
     *
     * @param sessionId 会话 id
     * @param chapterId 章节 id
     * @param model     指定模型（可空）
     * @param emitter   SSE 发射器
     */
    public void generateChapter(String sessionId, String chapterId, String model, SseEmitter emitter) {
        PaperSession session = paperSessionStore.get(sessionId);
        if (session == null) {
            sendError(emitter, "会话不存在或已过期");
            return;
        }

        TocNode node = findNode(session.getToc(), chapterId);
        if (PaperReferenceContentHelper.isReferenceChapter(chapterId, node)) {
            deliverReferenceChapter(sessionId, chapterId, session, emitter);
            return;
        }
        if (PaperChapterPrompts.isAbstractChapter(chapterId, node)) {
            PaperSession latest = paperSessionStore.get(sessionId);
            generateAbstractChapter(sessionId, chapterId, latest != null ? latest : session, model, emitter);
            return;
        }

        String userPrompt;
        try {
            userPrompt = buildUserPrompt(session, chapterId);
        } catch (ServiceException e) {
            sendError(emitter, e.getMessage());
            return;
        }

        try {
            StreamingChatModel chatModel = buildStreamingModel(resolveModelName(model));
            boolean noCitation = PaperChapterPrompts.isNoCitationChapter(chapterId, node, session.getToc());
            String systemPrompt = noCitation
                ? PaperWritingStandards.SYSTEM_ROLE_NO_CITATION
                : SYSTEM_PROMPT;
            List<ChatMessage> messages = List.of(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt));

            sendEvent(emitter, Map.of("type", "start", "chapterId", chapterId));
            markChapterStatus(sessionId, chapterId, "generating");

            log.info("AI 逐章生成, sessionId={}, chapterId={}, noCitation={}", sessionId, chapterId, noCitation);
            chatModel.chat(messages, new ChapterStreamHandler(
                sessionId, chapterId, session, resolveModelName(model), emitter));
        } catch (ServiceException e) {
            sendError(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("章节生成失败, sessionId={}, chapterId={}", sessionId, chapterId, e);
            sendError(emitter, "章节生成失败，请稍后重试");
        }
    }

    /**
     * 流式回调：实时推送增量、完成后落库。
     */
    private class ChapterStreamHandler implements StreamingChatResponseHandler {

        private final String sessionId;
        private final String chapterId;
        private final PaperSession session;
        private final String modelName;
        private final SseEmitter emitter;
        private final StringBuilder buffer = new StringBuilder();

        ChapterStreamHandler(String sessionId, String chapterId, PaperSession session,
                             String modelName, SseEmitter emitter) {
            this.sessionId = sessionId;
            this.chapterId = chapterId;
            this.session = session;
            this.modelName = modelName;
            this.emitter = emitter;
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            if (partialResponse == null) {
                return;
            }
            buffer.append(partialResponse);
            sendEvent(emitter, Map.of("type", "content", "chapterId", chapterId, "delta", partialResponse));
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            String fullContent = buffer.toString();
            if (StringUtils.isBlank(fullContent) && completeResponse != null && completeResponse.aiMessage() != null) {
                fullContent = completeResponse.aiMessage().text();
            }
            PaperSession currentSession = session != null ? session : paperSessionStore.get(sessionId);
            TocNode node = currentSession == null ? null : findNode(currentSession.getToc(), chapterId);
            if (currentSession != null
                && PaperChapterPrompts.isAbstractChapter(chapterId, node)
                && !PaperChapterPrompts.hasEnglishAbstract(fullContent)) {
                log.warn("摘要缺英文 ABSTRACT，补生成英文段, sessionId={}", sessionId);
                try {
                    String chinese = extractChinesePart(fullContent.trim());
                    finalizeAbstractWithEnglish(sessionId, chapterId, currentSession, modelName, emitter, chinese);
                } catch (Exception e) {
                    log.error("补生成英文 ABSTRACT 失败, sessionId={}", sessionId, e);
                    savePartialChinese(sessionId, chapterId, extractChinesePart(fullContent.trim()));
                    markChapterStatus(sessionId, chapterId, "pending");
                    sendError(emitter, e instanceof ServiceException se ? se.getMessage() : "英文摘要生成失败，请重试");
                }
                return;
            }
            finalizeChapter(sessionId, chapterId, fullContent, emitter);
        }

        @Override
        public void onError(Throwable error) {
            log.error("章节流式生成出错, sessionId={}, chapterId={}", sessionId, chapterId, error);
            markChapterStatus(sessionId, chapterId, "pending");
            sendError(emitter, "AI 生成中断，请重试");
        }
    }

    // ---------------- Prompt 构建 ----------------

    /**
     * 根据 chapterId / 标题匹配章节 Prompt（{@link PaperChapterPrompts}），并拼接通用上下文。
     */
    private String buildUserPrompt(PaperSession session, String chapterId) {
        PaperSession.SqlParsed sqlParsed = session.getSqlParsed();
        TocNode node = findNode(session.getToc(), chapterId);

        PaperChapterPrompts.PromptContext promptCtx = buildPromptContext(session, sqlParsed, node);
        String base = PaperChapterPrompts.resolve(session, chapterId, node, promptCtx);
        Integer totalWords = session.getUserInputs() != null ? session.getUserInputs().getWordCount() : null;
        base = PaperChapterPrompts.withWordLimit(base, node, totalWords);

        return appendCommonContext(base, session, node);
    }

    private String appendCommonContext(String base, PaperSession session, TocNode node) {
        PaperSession.SqlParsed sqlParsed = session.getSqlParsed();
        String summary = sqlParsed == null ? null : sqlParsed.getSummary();

        StringBuilder ctx = new StringBuilder(base);
        ctx.append("\n\n----- 上下文 -----");
        if (StringUtils.isNotBlank(session.getTitle())) {
            ctx.append("\n【论文题目】").append(session.getTitle());
        }
        if (session.getUserInputs() != null && session.getUserInputs().getWordCount() != null) {
            ctx.append("\n【全文目标字数】").append(session.getUserInputs().getWordCount()).append(" 字");
        }
        if (node != null && node.getWordLimit() != null && node.getWordLimit() > 0) {
            ctx.append("\n【本节目标字数】").append(node.getWordLimit()).append(" 字");
        }
        if (StringUtils.isNotBlank(summary)) {
            ctx.append("\n【系统功能简介】").append(summary);
        }
        ctx.append("\n【写作规范】正文描述数据库与业务模块时请使用中文表名（如「用户信息表」「订单表」），")
            .append("禁止出现 sys_xxx、tb_xxx 等 SQL 物理表名；占位符与表结构插入仍按中文表名书写。");
        String outline = tocOutline(session.getToc());
        if (StringUtils.isNotBlank(outline)) {
            ctx.append("\n【论文整体大纲（保持章节连贯、避免与其他章节重复）】\n").append(outline);
        }
        String chapterId = node != null ? node.getId() : null;
        boolean noCitation = PaperChapterPrompts.isNoCitationChapter(chapterId, node, session.getToc());
        String refsList = formatRefsForCitation(session);
        if (!noCitation
            && StringUtils.isNotBlank(refsList)
            && !PaperReferenceContentHelper.isReferenceChapter(chapterId, node)
            && !isAcknowledgementTitle(node)) {
            ctx.append("\n【可引用参考文献（正文只用[n]角标，勿输出完整引文）】\n").append(refsList);
        }
        if (noCitation) {
            ctx.append("\n【文献引用】本节为系统设计 / 系统实现 / 系统测试类正文，禁止出现文献角标[n]及任何参考文献引用。");
            ctx.append(PaperWritingStandards.USER_APPENDIX_NO_CITATION);
        } else {
            ctx.append(PaperWritingStandards.USER_APPENDIX);
        }
        return ctx.toString();
    }

    /** 供正文角标引用的简表：[n] 作者/题名摘要 */
    private String formatRefsForCitation(PaperSession session) {
        List<Reference> refs = selectReferences(session, null, 0);
        if (refs == null || refs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Reference r : refs) {
            if (r == null || r.getIndex() == null) {
                continue;
            }
            sb.append('[').append(r.getIndex()).append("] ");
            if (StringUtils.isNotBlank(r.getCitation())) {
                sb.append(r.getCitation());
            } else {
                if (StringUtils.isNotBlank(r.getAuthor())) {
                    sb.append(r.getAuthor()).append('.');
                }
                if (StringUtils.isNotBlank(r.getTitle())) {
                    sb.append(r.getTitle());
                }
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static boolean isAcknowledgementTitle(TocNode node) {
        if (node == null) {
            return false;
        }
        String id = node.getId() == null ? "" : node.getId().toLowerCase();
        if ("acknowledgement".equals(id) || "thanks".equals(id) || id.contains("acknowledg")) {
            return true;
        }
        return StringUtils.isNotBlank(node.getTitle()) && node.getTitle().contains("致谢");
    }

    private PaperChapterPrompts.PromptContext buildPromptContext(PaperSession session,
                                                                 PaperSession.SqlParsed sqlParsed,
                                                                 TocNode node) {
        PaperSession.UserInputs inputs = session.getUserInputs();
        String envInfo = inputs != null && StringUtils.isNotBlank(inputs.getEnvInfo())
            ? inputs.getEnvInfo()
            : "（未提供开发环境，请结合常见 Java/Spring Boot/Vue/MySQL 技术栈合理推断）";
        String code = inputs != null ? inputs.getCodeContent() : null;
        String chapterTitle = node != null ? node.getTitle() : null;
        return new PaperChapterPrompts.PromptContext(
            session.getTitle() == null ? "" : session.getTitle(),
            envInfo,
            PaperCodeSnippetHelper.snippet(code, chapterTitle),
            sqlParsedText(sqlParsed),
            columnsText(sqlParsed),
            tablesText(sqlParsed),
            PaperChapterPrompts.inferErEntityLabels(sqlParsed),
            PaperChapterPrompts.inferErRelationSummary(sqlParsed),
            PaperChapterPrompts.inferDbTableLabels(sqlParsed),
            refsJson(selectReferences(session, null, 3)),
            refsJson(selectReferences(session, "zh", 4)),
            refsJson(selectReferences(session, "en", 4)),
            refsJson(selectReferences(session, null, 0))
        );
    }

    // ---------------- 上下文拼装辅助 ----------------

    /**
     * 选取参考文献子集。language 为 null 取全部，否则按语言过滤；limit<=0 表示不限。
     */
    private List<Reference> selectReferences(PaperSession session, String language, int limit) {
        List<Reference> refs = session.getReferences();
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        return refs.stream()
            .filter(r -> language == null || language.equalsIgnoreCase(r.getLanguage()))
            .limit(limit <= 0 ? Long.MAX_VALUE : limit)
            .collect(Collectors.toList());
    }

    private String refsJson(List<Reference> refs) {
        if (refs == null || refs.isEmpty()) {
            return "（暂无参考文献，请合理拟定并标注角标）";
        }
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (Exception e) {
            return refs.stream()
                .map(r -> "[" + r.getIndex() + "] " + r.getCitation())
                .collect(Collectors.joining("\n"));
        }
    }

    private String sqlParsedText(PaperSession.SqlParsed sqlParsed) {
        if (sqlParsed == null || sqlParsed.getTables() == null || sqlParsed.getTables().isEmpty()) {
            return "（未提供数据库结构）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("（下列为数据库结构参考；正文请用中文表名，勿写物理表名）");
        Map<String, List<SqlColumnInfo>> columns = sqlParsed.getColumns();
        for (String table : sqlParsed.getTables()) {
            String label = PaperTableLabelResolver.resolveTableLabel(table, sqlParsed);
            sb.append("\n表「").append(label).append("」：");
            List<SqlColumnInfo> cols = columns == null ? null : columns.get(table);
            if (cols != null) {
                for (SqlColumnInfo c : cols) {
                    sb.append("\n  - ").append(c.getName()).append(' ').append(c.getType());
                    if (StringUtils.isNotBlank(c.getComment())) {
                        sb.append(" (").append(c.getComment()).append(')');
                    }
                    if (c.isPk()) {
                        sb.append(" [PK]");
                    }
                    if (c.isFk()) {
                        sb.append(" [FK]");
                    }
                }
            }
        }
        if (sqlParsed.getRelations() != null && !sqlParsed.getRelations().isEmpty()) {
            sb.append("\n表关系（中文名）：");
            sqlParsed.getRelations().forEach(r ->
                sb.append("\n  - ")
                    .append(PaperTableLabelResolver.resolveTableLabel(r.getTable1(), sqlParsed))
                    .append(' ').append(r.getType()).append(' ')
                    .append(PaperTableLabelResolver.resolveTableLabel(r.getTable2(), sqlParsed))
                    .append("（关联字段 ").append(r.getViaColumn()).append('）'));
        }
        return sb.toString();
    }

    private String columnsText(PaperSession.SqlParsed sqlParsed) {
        return sqlParsedText(sqlParsed);
    }

    private String tablesText(PaperSession.SqlParsed sqlParsed) {
        if (sqlParsed == null || sqlParsed.getTables() == null || sqlParsed.getTables().isEmpty()) {
            return "（未提供功能模块）";
        }
        return PaperTableLabelResolver.joinTableLabels(sqlParsed.getTables(), sqlParsed);
    }

    private String tocOutline(List<TocNode> toc) {
        if (toc == null || toc.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendOutline(toc, sb);
        return sb.toString();
    }

    private void appendOutline(List<TocNode> nodes, StringBuilder sb) {
        for (TocNode node : nodes) {
            int level = node.getLevel() == null ? 1 : node.getLevel();
            sb.append("  ".repeat(Math.max(0, level - 1)))
                .append(node.getTitle() == null ? node.getId() : node.getTitle())
                .append('\n');
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                appendOutline(node.getChildren(), sb);
            }
        }
    }

    private TocNode findNode(List<TocNode> nodes, String chapterId) {
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

    private void markChapterStatus(String sessionId, String chapterId, String status) {
        paperSessionStore.update(sessionId, s -> {
            TocNode node = findNode(s.getToc(), chapterId);
            if (node != null) {
                node.setStatus(status);
            }
        });
    }

    // ---------------- 模型 / SSE ----------------

    private StreamingChatModel buildStreamingModel(String modelName) {
        ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
        if (modelVo == null) {
            throw new ServiceException("模型不存在: " + modelName);
        }
        return OpenAiStreamingChatModel.builder()
            .baseUrl(modelVo.getApiHost())
            .apiKey(modelVo.getApiKey())
            .modelName(modelVo.getModelName())
            .timeout(CHAT_TIMEOUT)
            .build();
    }

    private String resolveModelName(String requestModel) {
        if (StringUtils.isNotBlank(requestModel)) {
            return requestModel;
        }
        String defaultModel = erDiagramProperties.getDefaultModel();
        if (StringUtils.isBlank(defaultModel)) {
            throw new ServiceException("未指定模型且未配置 chat.model.default-model");
        }
        return defaultModel;
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
