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
import org.ruoyi.domain.dto.request.PaperRewriteSegmentRequest;
import org.ruoyi.domain.dto.request.PaperSaveChapterRequest;
import org.ruoyi.domain.dto.request.PaperScreenshotsAnalyzeRequest;
import org.ruoyi.domain.dto.request.PaperScreenshotsSaveRequest;
import org.ruoyi.domain.dto.request.PaperSessionFormatUpdateRequest;
import org.ruoyi.domain.dto.request.PaperLitOnDemandStartRequest;
import org.ruoyi.domain.dto.request.PaperTocRequest;
import org.ruoyi.domain.dto.request.PaperUpdateTocRequest;
import org.ruoyi.domain.dto.request.PaperUpdateInputsRequest;
import org.ruoyi.domain.dto.response.PaperParseSqlResponse;
import org.ruoyi.domain.dto.response.PaperRewriteSegmentResultVo;
import org.ruoyi.domain.dto.response.PaperScreenshotsAnalyzeResponse;
import org.ruoyi.domain.dto.response.PaperSessionFormatVo;
import org.ruoyi.domain.vo.paper.LitOnDemandStatusVo;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperSessionSummary;
import org.ruoyi.domain.paper.PaperSession.SqlParsed;
import org.ruoyi.domain.paper.PaperUiScreenshot;
import org.ruoyi.domain.paper.TocNode;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final PaperDefensePptService paperDefensePptService;
    private final PaperAssetService paperAssetService;
    private final PaperSqlErOptimizer paperSqlErOptimizer;
    private final PaperFormatTemplateService paperFormatTemplateService;
    private final PaperSessionCustomFormatService paperSessionCustomFormatService;
    private final PaperRewriteService paperRewriteService;
    private final PaperScreenshotService paperScreenshotService;
    private final LitOnDemandService litOnDemandService;

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
     * 论文写作选区改写：扩写 / 缩写 / 润色（降重、降 AI 率请走既有 document 接口）。
     */
    @PostMapping("/rewrite-segment")
    public R<PaperRewriteSegmentResultVo> rewriteSegment(@RequestBody @Valid PaperRewriteSegmentRequest request) {
        return R.ok(paperRewriteService.rewrite(request));
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
     * 查询会话排版配置（模板、覆盖、合并结果、内置默认）。
     */
    @GetMapping("/session/{sessionId}/format")
    public R<PaperSessionFormatVo> getSessionFormat(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        PaperSession session = paperSessionStore.require(sessionId, userId);
        return R.ok(toSessionFormatVo(session));
    }

    /**
     * 更新会话排版配置（切换模板 / 稀疏覆盖 / 清空覆盖）。
     */
    @PutMapping("/session/{sessionId}/format")
    public R<PaperSessionFormatVo> updateSessionFormat(
        @PathVariable String sessionId,
        @RequestBody(required = false) PaperSessionFormatUpdateRequest request) {
        Long userId = LoginHelper.getUserId();
        PaperSession session = paperSessionStore.require(sessionId, userId);
        if (request == null) {
            request = new PaperSessionFormatUpdateRequest();
        }

        boolean templateIdSpecified = request.isTemplateIdSpecified();
        Long requestedTemplateId = request.getTemplateId();
        Boolean clearOverride = request.getClearOverride();
        if (templateIdSpecified
            && !Objects.equals(session.getFormatTemplateId(), requestedTemplateId)
            && clearOverride == null) {
            clearOverride = true;
        }

        PaperFormatConfig override = request.getOverride();
        Boolean finalClearOverride = clearOverride;
        paperSessionStore.update(sessionId, s -> {
            if (templateIdSpecified) {
                if (PaperSessionCustomFormatService.isCustomMode(s)) {
                    paperSessionCustomFormatService.clearCustomDocx(s, userId);
                }
                s.setFormatTemplateId(requestedTemplateId);
            }
            if (Boolean.TRUE.equals(finalClearOverride)) {
                s.setFormatOverrideJson(null);
            } else if (override != null) {
                s.setFormatOverrideJson(PaperFormatMerger.toJson(override));
                // 校验合并结果（含自定义模式）
                paperFormatTemplateService.resolveEffective(s);
            }
        });
        return R.ok(toSessionFormatVo(paperSessionStore.require(sessionId, userId)));
    }

    /**
     * 上传会话自定义本校排版模板（docx + 可选 format / patchStyles）。
     */
    @PostMapping(value = "/session/{sessionId}/format/custom-docx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<PaperSessionFormatVo> uploadCustomFormatDocx(
        @PathVariable String sessionId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(value = "format", required = false) String formatJson,
        @RequestParam(value = "patchStyles", required = false) Boolean patchStyles) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(sessionId, userId);
        PaperFormatConfig format = null;
        if (StringUtils.isNotBlank(formatJson)) {
            try {
                format = PaperFormatMerger.parseJson(formatJson);
            } catch (IllegalArgumentException e) {
                throw new ServiceException(e.getMessage());
            }
        }
        PaperFormatConfig finalFormat = format;
        paperSessionStore.update(sessionId, s ->
            paperSessionCustomFormatService.saveCustomDocx(s, userId, file, finalFormat, patchStyles));
        return R.ok(toSessionFormatVo(paperSessionStore.require(sessionId, userId)));
    }

    /**
     * 清除会话自定义排版模板。
     */
    @DeleteMapping("/session/{sessionId}/format/custom-docx")
    public R<PaperSessionFormatVo> deleteCustomFormatDocx(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(sessionId, userId);
        paperSessionStore.update(sessionId, s ->
            paperSessionCustomFormatService.clearCustomDocx(s, userId));
        return R.ok(toSessionFormatVo(paperSessionStore.require(sessionId, userId)));
    }

    /**
     * 重置会话排版覆盖（仅清空 formatOverrideJson，保留模板绑定 / 自定义 docx）。
     */
    @PostMapping("/session/{sessionId}/format/reset")
    public R<PaperSessionFormatVo> resetSessionFormat(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(sessionId, userId);
        paperSessionStore.update(sessionId, s -> s.setFormatOverrideJson(null));
        return R.ok(toSessionFormatVo(paperSessionStore.require(sessionId, userId)));
    }

    private PaperSessionFormatVo toSessionFormatVo(PaperSession session) {
        PaperSessionFormatVo vo = new PaperSessionFormatVo();
        boolean custom = PaperSessionCustomFormatService.isCustomMode(session);
        vo.setMode(custom ? "custom" : "school");
        vo.setHasCustomDocx(custom);
        vo.setCustomDocxName(session.getCustomFormatDocxName());
        vo.setCustomPatchStyles(custom
            && (session.getCustomPatchStyles() == null || session.getCustomPatchStyles() != 0));
        if (StringUtils.isNotBlank(session.getCustomFormatJson())) {
            try {
                vo.setCustomFormat(PaperFormatMerger.parseJson(session.getCustomFormatJson()));
            } catch (IllegalArgumentException e) {
                throw new ServiceException(e.getMessage());
            }
        }
        vo.setTemplateId(session.getFormatTemplateId());
        if (StringUtils.isNotBlank(session.getFormatOverrideJson())) {
            try {
                vo.setOverride(PaperFormatMerger.parseJson(session.getFormatOverrideJson()));
            } catch (IllegalArgumentException e) {
                throw new ServiceException(e.getMessage());
            }
        } else {
            vo.setOverride(null);
        }
        vo.setEffective(paperFormatTemplateService.resolveEffective(session));
        vo.setDefaults(PaperFormatDefaults.dalianOcean());
        return vo;
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
        boolean hasUiScreenshots = session.getUiScreenshots() != null
            && session.getUiScreenshots().stream().anyMatch(g -> {
                if (g == null) {
                    return false;
                }
                if (g.getImages() != null && g.getImages().stream()
                    .anyMatch(img -> img != null && org.ruoyi.common.core.utils.StringUtils.isNotBlank(img.getAssetUrl()))) {
                    return true;
                }
                return org.ruoyi.common.core.utils.StringUtils.isNotBlank(g.getAssetUrl());
            });
        // 第五章完全由功能界面截图驱动：未上传截图时跳过自动刷新，既不抛异常也不回退到 SQL 表结构。
        if (session.getToc() != null && !session.getToc().isEmpty() && hasUiScreenshots) {
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
     * 保存系统实现功能界面截图清单（整表覆盖，管理员/用户两侧合并提交）。
     */
    @PutMapping("/screenshots")
    public R<Void> saveScreenshots(@RequestBody @Valid PaperScreenshotsSaveRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(request.getSessionId(), userId);
        paperScreenshotService.save(request.getSessionId(), request.getScreenshots());
        return R.ok();
    }

    /**
     * 调用视觉模型识别截图对应的功能名称；items 为空则识别会话内当前全部截图。
     */
    @PostMapping("/screenshots/analyze")
    public R<PaperScreenshotsAnalyzeResponse> analyzeScreenshots(@RequestBody @Valid PaperScreenshotsAnalyzeRequest request) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(request.getSessionId(), userId);
        return R.ok(paperScreenshotService.analyze(request.getSessionId(), request.getItems()));
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
     * 按题目拆词启动按需文献抓取（异步），与大纲生成并行调用。
     */
    @PostMapping("/lit-ondemand/start")
    public R<Map<String, String>> startLitOnDemand(@RequestBody @Valid PaperLitOnDemandStartRequest request) {
        Long userId = LoginHelper.getUserId();
        String taskId = litOnDemandService.start(request.getSessionId(), userId);
        return R.ok(Map.of("taskId", taskId));
    }

    /**
     * 查询按需文献抓取进度。
     */
    @GetMapping("/lit-ondemand/{taskId}")
    public R<LitOnDemandStatusVo> litOnDemandStatus(@PathVariable String taskId) {
        Long userId = LoginHelper.getUserId();
        return R.ok(litOnDemandService.getStatus(taskId, userId));
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

    /**
     * 导出答辩 PPT（.pptx）：按论文大纲拆页，从已生成章节提炼要点。
     */
    @GetMapping("/export-ppt/{sessionId}")
    public ResponseEntity<byte[]> exportPpt(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        paperSessionStore.require(sessionId, userId);
        byte[] data = paperDefensePptService.export(sessionId);
        String fileName = paperDefensePptService.resolveTitle(sessionId) + "-答辩.pptx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .header(HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation")
            .body(data);
    }
}
