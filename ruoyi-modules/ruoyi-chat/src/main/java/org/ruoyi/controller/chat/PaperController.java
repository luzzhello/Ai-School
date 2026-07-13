package org.ruoyi.controller.chat;

import jakarta.servlet.http.HttpServletRequest;
import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.PaperCreateSessionRequest;
import org.ruoyi.domain.dto.request.PaperParseSqlRequest;
import org.ruoyi.domain.dto.request.PaperConfirmReferencesRequest;
import org.ruoyi.domain.dto.request.PaperGenerateChapterRequest;
import org.ruoyi.domain.dto.request.PaperReferencesRequest;
import org.ruoyi.domain.dto.request.PaperSaveChapterRequest;
import org.ruoyi.domain.dto.request.PaperTocRequest;
import org.ruoyi.domain.dto.request.PaperUpdateTocRequest;
import org.ruoyi.domain.dto.request.PaperUpdateInputsRequest;
import org.ruoyi.domain.dto.response.PaperParseSqlResponse;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperSessionSummary;
import org.ruoyi.domain.paper.PaperSession.SqlParsed;
import org.ruoyi.domain.paper.TocNode;
import org.ruoyi.service.paper.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 论文生成智能体。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/paper")
public class PaperController {

    private final SqlParserService sqlParserService;
    private final PaperSessionStore paperSessionStore;
    private final PaperReferenceService paperReferenceService;
    private final PaperTocService paperTocService;
    private final PaperGenerateService paperGenerateService;
    private final WordExportService wordExportService;
    private final PaperAssetService paperAssetService;
    private final PaperSqlErOptimizer paperSqlErOptimizer;

    /**
     * 创建论文生成会话。题目与基础输入可选，创建后返回 sessionId。
     */
    @PostMapping("/session")
    public R<PaperSession> createSession(@RequestBody(required = false) PaperCreateSessionRequest request) {
        Long userId = LoginHelper.getUserId();
        PaperSession session = paperSessionStore.create(userId);
        if (request != null) {
            paperSessionStore.update(session.getSessionId(), s -> {
                if (StringUtils.isNotBlank(request.getTitle())) {
                    s.setTitle(request.getTitle());
                }
                if (request.getCodeContent() != null) {
                    s.getUserInputs().setCodeContent(request.getCodeContent());
                }
                if (request.getEnvInfo() != null) {
                    s.getUserInputs().setEnvInfo(request.getEnvInfo());
                }
                if (request.getWordCount() != null) {
                    s.getUserInputs().setWordCount(request.getWordCount());
                }
                if (StringUtils.isNotBlank(request.getEducationLevel())) {
                    s.getUserInputs().setEducationLevel(request.getEducationLevel());
                }
            });
        }
        return R.ok(paperSessionStore.get(session.getSessionId()));
    }

    /**
     * 查询会话详情。
     */
    @GetMapping("/session/{sessionId}")
    public R<PaperSession> getSession(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        PaperSession session = paperSessionStore.require(sessionId, userId);
        return R.ok(session);
    }

    /**
     * 当前用户的论文会话历史列表（按更新时间倒序）。
     */
    @GetMapping("/sessions")
    public R<List<PaperSessionSummary>> listSessions() {
        Long userId = LoginHelper.getUserId();
        return R.ok(paperSessionStore.listByUser(userId, 50));
    }

    /**
     * 保存章节正文（含手动编辑）。
     */
    @PostMapping("/chapter/save")
    public R<Void> saveChapter(@RequestBody @Valid PaperSaveChapterRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.saveChapterContent(
            request.getSessionId(),
            userId,
            request.getChapterId(),
            request.getContent());
        return R.ok();
    }

    /**
     * 删除会话及数据库记录。
     */
    @DeleteMapping("/session/{sessionId}")
    public R<Void> removeSession(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(sessionId, userId);
        paperSessionStore.remove(sessionId);
        return R.ok();
    }

    /**
     * 更新会话输入（题目、字数、专业、代码等）。
     */
    @PostMapping("/inputs")
    public R<Void> updateInputs(@RequestBody @Valid PaperUpdateInputsRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(request.getSessionId(), userId);
        paperSessionStore.update(request.getSessionId(), s -> {
            if (StringUtils.isNotBlank(request.getTitle())) {
                s.setTitle(request.getTitle().trim());
            }
            if (request.getCodeContent() != null) {
                s.getUserInputs().setCodeContent(request.getCodeContent());
            }
            if (request.getEnvInfo() != null) {
                s.getUserInputs().setEnvInfo(request.getEnvInfo());
            }
            if (request.getWordCount() != null) {
                s.getUserInputs().setWordCount(request.getWordCount());
                if (s.getToc() != null && !s.getToc().isEmpty()) {
                    PaperWordLimitAllocator.apply(s.getToc(), request.getWordCount());
                }
            }
            if (StringUtils.isNotBlank(request.getEducationLevel())) {
                s.getUserInputs().setEducationLevel(request.getEducationLevel());
            }
        });
        return R.ok();
    }

    /**
     * 解析 SQL 文件，提取表结构并保存到会话。
     */
    @PostMapping("/parse-sql")
    public R<PaperParseSqlResponse> parseSql(@RequestBody @Valid PaperParseSqlRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(request.getSessionId(), userId);
        PaperSqlErOptimizer.OptimizeResult optimized = paperSqlErOptimizer.optimizeIfNeeded(request.getSqlContent());
        SqlParsed parsed = sqlParserService.parse(optimized.sql());
        String finalSql = optimized.sql();
        paperSessionStore.update(request.getSessionId(), s -> {
            s.getUserInputs().setSqlContent(finalSql);
            s.setSqlParsed(parsed);
        });
        List<TocNode> refreshedToc = null;
        boolean tocRefreshed = false;
        PaperSession session = paperSessionStore.get(request.getSessionId());
        if (session.getToc() != null && !session.getToc().isEmpty()) {
            refreshedToc = paperTocService.refreshChapter5(request.getSessionId());
            tocRefreshed = true;
        }
        return R.ok(PaperParseSqlResponse.builder()
            .parsed(parsed)
            .sqlContent(finalSql)
            .sqlOptimized(optimized.optimized())
            .toc(refreshedToc)
            .tocRefreshed(tocRefreshed)
            .build());
    }

    /**
     * 检索参考文献（SSE 流式逐条返回）。论文生成第一步，文献优先。
     */
    @PostMapping(value = "/references", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter references(@RequestBody @Valid PaperReferencesRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        paperReferenceService.generate(request, emitter);
        return emitter;
    }

    /**
     * 确认（锁定）参考文献，状态推进到 ref_confirmed。
     */
    @PostMapping("/references/confirm")
    public R<Void> confirmReferences(@RequestBody @Valid PaperConfirmReferencesRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(request.getSessionId(), userId);
        paperSessionStore.update(request.getSessionId(), s -> {
            if (request.getReferences() != null) {
                s.setReferences(request.getReferences());
            }
            s.setStatus(PaperSession.Status.REF_CONFIRMED);
            PaperReferenceContentHelper.syncReferenceChapter(s);
        });
        return R.ok();
    }

    /**
     * 生成论文目录大纲（树形），存入会话并返回。
     */
    @PostMapping("/toc")
    public R<List<TocNode>> toc(@RequestBody @Valid PaperTocRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(request.getSessionId(), userId);
        return R.ok(paperTocService.generate(
            request.getSessionId(),
            request.getModel(),
            Boolean.TRUE.equals(request.getUseDefaultTemplate())));
    }

    /**
     * 按当前 SQL 解析结果刷新第五章「系统实现」子模块（修复空泛功能名等）。
     */
    @PostMapping("/toc/refresh-ch5")
    public R<List<TocNode>> refreshChapter5Toc(@RequestBody @Valid PaperTocRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(request.getSessionId(), userId);
        return R.ok(paperTocService.refreshChapter5(request.getSessionId()));
    }

    /**
     * 保存用户编辑后的目录大纲。
     */
    @PostMapping("/toc/save")
    public R<Void> saveToc(@RequestBody @Valid PaperUpdateTocRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(request.getSessionId(), userId);
        paperSessionStore.update(request.getSessionId(), s -> {
            s.setToc(request.getToc());
            PaperReferenceContentHelper.syncReferenceChapter(s);
        });
        return R.ok();
    }

    /**
     * 逐章生成正文（SSE 流式输出，完成后写入会话）。
     */
    @PostMapping(value = "/generate-chapter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateChapter(@RequestBody @Valid PaperGenerateChapterRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        paperGenerateService.generateChapter(request.getSessionId(), request.getChapterId(), request.getModel(), emitter);
        return emitter;
    }

    /**
     * 上传论文插图（API 生成用例图等写入正文前调用，返回可访问 URL）。
     */
    @PostMapping(value = "/upload-asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, String>> uploadAsset(@RequestPart("file") MultipartFile file) {
        LoginHelper.getUserId();
        String url = paperAssetService.uploadImage(file);
        return R.ok(Map.of("url", url));
    }

    /**
     * 读取本地存储的论文插图（供前端预览与 Word 导出引用）。
     */
    @SaIgnore
    @GetMapping("/assets/**")
    public ResponseEntity<Resource> getAsset(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/api/paper/assets/";
        int idx = uri.indexOf(prefix);
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String relativePath = uri.substring(idx + prefix.length());
        Resource resource = paperAssetService.loadAsResource(relativePath);
        MediaType mediaType = paperAssetService.resolveMediaType(relativePath);
        return ResponseEntity.ok()
            .contentType(mediaType)
            .body(resource);
    }

    /**
     * 导出论文为 Word(.docx) 文件下载。
     */
    @GetMapping("/export/{sessionId}")
    public ResponseEntity<byte[]> export(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(sessionId, userId);
        byte[] data = wordExportService.export(sessionId);
        String fileName = wordExportService.resolveTitle(sessionId) + ".docx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .header(HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .body(data);
    }
}
