package org.ruoyi.service.paper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.SpringUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.LitPaperProperties;
import org.ruoyi.domain.entity.lit.LitPaperEnEntity;
import org.ruoyi.domain.entity.lit.LitPaperEntity;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.domain.vo.paper.LitOnDemandStatusVo;
import org.ruoyi.mapper.lit.LitPaperEnMapper;
import org.ruoyi.mapper.lit.LitPaperMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按题目拆词后异步调用知网爬虫，导入 lit_paper / lit_paper_en（中英各爬相同数量）。
 * <p>
 * 启动爬取前先尝试库内自动选用（{@link #tryAutoSelectFromDb}）：按题目分词检索
 * （每词 {@code searchPerKeyword} 条、合并最多 {@code searchMaxTotal} 条），若中英文
 * 结果均达到 {@code dbReadyMinCount}，直接从库内挑选文献写入会话，跳过爬虫，
 * 任务来源标记为 {@code db}；否则回退到实时爬取，来源标记为 {@code crawl}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LitOnDemandService {

    private final LitPaperProperties litPaperProperties;
    private final PaperSessionStore paperSessionStore;
    private final CnkiCrawlerProcessClient crawlerProcessClient;
    private final LitPaperMapper litPaperMapper;
    private final LitPaperEnMapper litPaperEnMapper;
    private final LitPaperSearchService litPaperSearchService;
    private final ObjectMapper objectMapper;

    private final Map<String, LitOnDemandTask> tasks = new ConcurrentHashMap<>();

    public String start(String sessionId, Long userId) {
        LitPaperProperties.OnDemand cfg = litPaperProperties.getOndemand();
        if (cfg == null || !cfg.isEnabled()) {
            throw new ServiceException("按需文献抓取未启用");
        }
        PaperSession session = paperSessionStore.require(sessionId, userId);
        if (StringUtils.isBlank(session.getTitle())) {
            throw new ServiceException("请先填写论文题目");
        }

        List<String> keywords = TitleKeywordSplitter.split(
            session.getTitle(), cfg.getMinKeywords(), cfg.getMaxKeywords());
        if (keywords.isEmpty()) {
            keywords = List.of(truncate(session.getTitle().trim(), 20));
        }

        LitOnDemandTask task = new LitOnDemandTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setSessionId(sessionId);
        task.setTitle(session.getTitle());
        task.setKeywords(new ArrayList<>(keywords));
        task.setOutlineStatus(LitOnDemandTask.Status.DONE);
        task.setLitStatus(LitOnDemandTask.Status.PENDING);
        task.setUserId(userId);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(task.getCreatedAt());
        tasks.put(task.getTaskId(), task);

        // 经 Spring 代理调用，确保 @Async 生效
        SpringUtils.getBean(LitOnDemandService.class).runAsync(task.getTaskId());
        return task.getTaskId();
    }

    public LitOnDemandStatusVo getStatus(String taskId, Long userId) {
        LitOnDemandTask task = tasks.get(taskId);
        if (task == null) {
            throw new ServiceException("文献任务不存在或已过期");
        }
        paperSessionStore.require(task.getSessionId(), userId);
        maybeMarkTimeout(task);
        return toVo(task);
    }

    /**
     * 库内文献充足时自动选用并写入会话；返回 true 表示已处理完毕、无需爬取。
     */
    boolean tryAutoSelectFromDb(LitOnDemandTask task) {
        LitPaperProperties.OnDemand cfg = litPaperProperties.getOndemand();
        int min = Math.max(1, cfg.getDbReadyMinCount());
        int takeZh = Math.max(0, cfg.getAutoSelectZh());
        int takeEn = Math.max(0, cfg.getAutoSelectEn());
        String title = task.getTitle();

        List<Reference> zh = litPaperSearchService.search(title, "zh", min);
        List<Reference> en = litPaperSearchService.search(title, "en", min);
        if (zh.size() < min || en.size() < min) {
            return false;
        }

        task.setSource("db");
        PaperSession session = paperSessionStore.require(task.getSessionId(), task.getUserId());
        if (session.getReferences() != null && !session.getReferences().isEmpty()) {
            task.setLitStatus(LitOnDemandTask.Status.DONE);
            task.setFetchedCountZh(0);
            task.setFetchedCountEn(0);
            task.setFetchedCount(0);
            task.setSelectedCountZh(0);
            task.setSelectedCountEn(0);
            task.setError(null);
            touch(task);
            return true;
        }

        List<Reference> picked = new ArrayList<>();
        for (int i = 0; i < takeZh && i < zh.size(); i++) {
            picked.add(zh.get(i));
        }
        for (int i = 0; i < takeEn && i < en.size(); i++) {
            picked.add(en.get(i));
        }
        for (int i = 0; i < picked.size(); i++) {
            picked.get(i).setIndex(i + 1);
        }
        paperSessionStore.update(task.getSessionId(), s -> {
            s.setReferences(picked);
            s.setStatus(PaperSession.Status.REF_CONFIRMED);
            PaperReferenceContentHelper.syncReferenceChapter(s);
        });
        task.setSelectedCountZh(Math.min(takeZh, zh.size()));
        task.setSelectedCountEn(Math.min(takeEn, en.size()));
        task.setFetchedCountZh(task.getSelectedCountZh());
        task.setFetchedCountEn(task.getSelectedCountEn());
        task.setFetchedCount(task.getSelectedCountZh() + task.getSelectedCountEn());
        task.setLitStatus(LitOnDemandTask.Status.DONE);
        task.setError(null);
        touch(task);
        return true;
    }

    /**
     * 会话尚无参考文献时，按配置从库内挑选写入会话。
     * @return true 表示已写入或确认会话已有文献；false 表示库内无可选
     */
    boolean autoSelectIntoSessionIfEmpty(LitOnDemandTask task) {
        LitPaperProperties.OnDemand cfg = litPaperProperties.getOndemand();
        int takeZh = Math.max(0, cfg.getAutoSelectZh());
        int takeEn = Math.max(0, cfg.getAutoSelectEn());
        PaperSession session = paperSessionStore.require(task.getSessionId(), task.getUserId());
        if (session.getReferences() != null && !session.getReferences().isEmpty()) {
            return true;
        }
        String title = task.getTitle();
        List<Reference> zh = takeZh > 0
            ? litPaperSearchService.search(title, "zh", Math.max(takeZh, 20))
            : List.of();
        List<Reference> en = takeEn > 0
            ? litPaperSearchService.search(title, "en", Math.max(takeEn, 5))
            : List.of();
        if (zh.isEmpty() && en.isEmpty()) {
            return false;
        }

        List<Reference> picked = new ArrayList<>();
        for (int i = 0; i < takeZh && i < zh.size(); i++) {
            picked.add(zh.get(i));
        }
        for (int i = 0; i < takeEn && i < en.size(); i++) {
            picked.add(en.get(i));
        }
        if (picked.isEmpty()) {
            return false;
        }
        for (int i = 0; i < picked.size(); i++) {
            picked.get(i).setIndex(i + 1);
        }

        paperSessionStore.update(task.getSessionId(), s -> {
            s.setReferences(picked);
            s.setStatus(PaperSession.Status.REF_CONFIRMED);
            PaperReferenceContentHelper.syncReferenceChapter(s);
        });

        task.setSelectedCountZh(Math.min(takeZh, zh.size()));
        task.setSelectedCountEn(Math.min(takeEn, en.size()));
        if (task.getFetchedCount() <= 0) {
            task.setFetchedCountZh(task.getSelectedCountZh());
            task.setFetchedCountEn(task.getSelectedCountEn());
            task.setFetchedCount(task.getSelectedCountZh() + task.getSelectedCountEn());
        }
        return true;
    }

    /** 爬取入库后：会话尚无文献则自动选用写入会话 */
    void maybeAutoSelectAfterCrawl(LitOnDemandTask task) {
        if (task == null || task.getFetchedCount() <= 0) {
            return;
        }
        try {
            autoSelectIntoSessionIfEmpty(task);
        } catch (Exception e) {
            log.warn("crawl 后自动选用文献失败 sessionId={}: {}", task.getSessionId(), e.getMessage());
        }
    }

    @Async
    public void runAsync(String taskId) {
        LitOnDemandTask task = tasks.get(taskId);
        if (task == null) {
            return;
        }
        LitPaperProperties.OnDemand cfg = litPaperProperties.getOndemand();
        task.setLitStatus(LitOnDemandTask.Status.RUNNING);
        touch(task);

        if (tryAutoSelectFromDb(task)) {
            log.info("lit on-demand skip crawl (db ready) taskId={} sessionId={}",
                taskId, task.getSessionId());
            return;
        }
        task.setSource("crawl");

        Path workTmp = null;
        try {
            workTmp = Files.createTempDirectory("lit-ondemand-" + taskId.substring(0, 8));
            Path jsonlZh = workTmp.resolve("papers_zh.jsonl");
            Path jsonlEn = workTmp.resolve("papers_en.jsonl");
            Path checkpointZh = workTmp.resolve("checkpoint_zh.json");
            Path checkpointEn = workTmp.resolve("checkpoint_en.json");

            // 中英各爬相同 maxPerKeyword，总耗时约为单语两倍
            int timeoutSec = Math.max(60, cfg.getTaskTimeoutSec());
            log.info(
                "lit on-demand crawl start taskId={} sessionId={} title={} keywords={} maxPerKeyword={} listOnly={} timeoutSec={} workDir={}",
                taskId,
                task.getSessionId(),
                truncate(task.getTitle(), 80),
                task.getKeywords(),
                cfg.getMaxPerKeyword(),
                cfg.isListOnly(),
                timeoutSec,
                workTmp);

            CnkiCrawlerProcessClient.CrawlTaskResult result = crawlerProcessClient.runCrawlTask(
                task.getKeywords(),
                cfg.getMaxPerKeyword(),
                cfg.isListOnly(),
                "both",
                jsonlZh,
                checkpointZh,
                jsonlEn,
                checkpointEn,
                Duration.ofSeconds(timeoutSec));

            ImportStats statsZh = importJsonlZhStats(jsonlZh, task.getKeywords());
            ImportStats statsEn = importJsonlEnStats(jsonlEn, task.getKeywords());
            int importedZh = statsZh.imported;
            int importedEn = statsEn.imported;
            int imported = importedZh + importedEn;
            task.setFetchedCountZh(importedZh);
            task.setFetchedCountEn(importedEn);
            task.setFetchedCount(imported);

            String diagnosis = buildImportDiagnosis(task.getKeywords(), result.exitCode(), statsZh, statsEn);
            log.info(
                "lit on-demand crawl finished taskId={} exit={} zh[lines={},imported={},dup={},blank={},missing={}] "
                    + "en[lines={},imported={},dup={},blank={},missing={}] diagnosis={}",
                taskId,
                result.exitCode(),
                statsZh.lines, statsZh.imported, statsZh.duplicate, statsZh.blankTitle, statsZh.fileMissing,
                statsEn.lines, statsEn.imported, statsEn.duplicate, statsEn.blankTitle, statsEn.fileMissing,
                diagnosis);
            if (StringUtils.isNotBlank(result.logTail())) {
                log.info("lit on-demand crawl logTail taskId={}:\n{}", taskId, result.logTail());
            }

            if (result.exitCode() != 0) {
                task.setLitStatus(imported > 0 ? LitOnDemandTask.Status.PARTIAL : LitOnDemandTask.Status.FAILED);
                task.setError(withCrawlLog(diagnosis, result.logTail(), 1500));
            } else if (imported == 0) {
                task.setLitStatus(LitOnDemandTask.Status.FAILED);
                task.setError(withCrawlLog(diagnosis, result.logTail(), 1500));
            } else if (importedZh == 0 || importedEn == 0) {
                task.setLitStatus(LitOnDemandTask.Status.PARTIAL);
                task.setError(withCrawlLog(diagnosis, result.logTail(), 1500));
            } else {
                task.setLitStatus(LitOnDemandTask.Status.DONE);
                task.setError(null);
            }
            if (imported > 0) {
                maybeAutoSelectAfterCrawl(task);
            }
        } catch (Exception e) {
            log.warn("lit on-demand task failed taskId={}: {}", taskId, e.getMessage(), e);
            if (task.getFetchedCount() > 0) {
                task.setLitStatus(LitOnDemandTask.Status.PARTIAL);
                maybeAutoSelectAfterCrawl(task);
            } else {
                task.setLitStatus(LitOnDemandTask.Status.FAILED);
            }
            task.setError(truncate(e.getMessage(), 500));
        } finally {
            touch(task);
            if (workTmp != null) {
                try {
                    Files.walk(workTmp)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                                // ignore cleanup errors
                            }
                        });
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private void maybeMarkTimeout(LitOnDemandTask task) {
        if (!LitOnDemandTask.Status.RUNNING.equals(task.getLitStatus())
            && !LitOnDemandTask.Status.PENDING.equals(task.getLitStatus())) {
            return;
        }
        int timeoutSec = litPaperProperties.getOndemand().getTaskTimeoutSec();
        if (timeoutSec <= 0) {
            return;
        }
        Instant deadline = task.getCreatedAt().plusSeconds(timeoutSec + 30L);
        if (Instant.now().isAfter(deadline)) {
            task.setLitStatus(task.getFetchedCount() > 0
                ? LitOnDemandTask.Status.PARTIAL
                : LitOnDemandTask.Status.FAILED);
            if (StringUtils.isBlank(task.getError())) {
                task.setError("任务超时");
            }
            touch(task);
        }
    }

    int importJsonl(Path jsonl, List<String> keywords) throws Exception {
        return importJsonlZh(jsonl, keywords);
    }

    int importJsonlZh(Path jsonl, List<String> keywords) throws Exception {
        return importJsonlZhStats(jsonl, keywords).imported;
    }

    int importJsonlEn(Path jsonl, List<String> keywords) throws Exception {
        return importJsonlEnStats(jsonl, keywords).imported;
    }

    ImportStats importJsonlZhStats(Path jsonl, List<String> keywords) throws Exception {
        return importJsonlStats(jsonl, keywords, true);
    }

    ImportStats importJsonlEnStats(Path jsonl, List<String> keywords) throws Exception {
        return importJsonlStats(jsonl, keywords, false);
    }

    private ImportStats importJsonlStats(Path jsonl, List<String> keywords, boolean chinese) throws Exception {
        ImportStats stats = new ImportStats();
        if (jsonl == null || !Files.isRegularFile(jsonl)) {
            stats.fileMissing = true;
            return stats;
        }
        String fallbackKeyword = keywords == null || keywords.isEmpty() ? null : keywords.get(0);
        try (BufferedReader reader = Files.newBufferedReader(jsonl, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                stats.lines++;
                JsonNode node = objectMapper.readTree(line);
                UpsertOutcome outcome = chinese
                    ? upsertOneZhOutcome(node, fallbackKeyword)
                    : upsertOneEnOutcome(node, fallbackKeyword);
                switch (outcome) {
                    case IMPORTED -> stats.imported++;
                    case DUPLICATE -> stats.duplicate++;
                    case BLANK_TITLE -> stats.blankTitle++;
                }
            }
        }
        return stats;
    }

    private enum UpsertOutcome {
        IMPORTED,
        DUPLICATE,
        BLANK_TITLE
    }

    static final class ImportStats {
        int lines;
        int imported;
        int duplicate;
        int blankTitle;
        boolean fileMissing;
    }

    /**
     * 根据爬取/入库统计生成可读诊断（用于前端 error 与服务端日志）。
     */
    static String buildImportDiagnosis(List<String> keywords, int exitCode, ImportStats zh, ImportStats en) {
        String kw = keywords == null || keywords.isEmpty() ? "-" : String.join(",", keywords);
        String zhPart = formatLangStats("中文", zh);
        String enPart = formatLangStats("英文", en);
        int crawled = zh.lines + en.lines;
        int imported = zh.imported + en.imported;
        int dup = zh.duplicate + en.duplicate;
        int blank = zh.blankTitle + en.blankTitle;

        if (exitCode != 0) {
            return "爬虫异常退出 exit=" + exitCode
                + "；关键词=[" + kw + "]；" + zhPart + "；" + enPart;
        }
        if (crawled == 0) {
            return "爬虫无结果（JSONL 为空）；关键词=[" + kw + "]；" + zhPart + "；" + enPart
                + "（可能无检索命中、Cookie/暖场失败或被风控）";
        }
        if (imported == 0 && dup > 0 && blank == 0) {
            return "爬取到 " + crawled + " 条但全部重复未入库；关键词=[" + kw + "]；"
                + zhPart + "；" + enPart;
        }
        if (imported == 0 && blank > 0) {
            return "爬取到 " + crawled + " 条但无法入库（缺标题 " + blank + " / 重复 " + dup + "）；关键词=["
                + kw + "]；" + zhPart + "；" + enPart;
        }
        if (zh.imported == 0 || en.imported == 0) {
            return "部分语言未入库：中文 " + zh.imported + " / 英文 " + en.imported
                + "；关键词=[" + kw + "]；" + zhPart + "；" + enPart;
        }
        return "入库完成：中文 " + zh.imported + " / 英文 " + en.imported
            + "；关键词=[" + kw + "]；" + zhPart + "；" + enPart;
    }

    private static String formatLangStats(String label, ImportStats stats) {
        if (stats.fileMissing) {
            return label + "文件缺失";
        }
        return label + "爬取" + stats.lines + "条/新入库" + stats.imported
            + "/重复" + stats.duplicate + "/缺标题" + stats.blankTitle;
    }

    static String withCrawlLog(String diagnosis, String logTail, int maxLen) {
        String base = StringUtils.blankToDefault(diagnosis, "文献获取失败");
        if (StringUtils.isBlank(logTail)) {
            return truncate(base, maxLen);
        }
        String compact = logTail.replace("\r\n", "\n").trim();
        if (compact.length() > 800) {
            compact = "…" + compact.substring(compact.length() - 800);
        }
        return truncate(base + " | 爬虫日志: " + compact, maxLen);
    }

    private UpsertOutcome upsertOneZhOutcome(JsonNode node, String fallbackKeyword) {
        String title = text(node, "title");
        if (StringUtils.isBlank(title)) {
            return UpsertOutcome.BLANK_TITLE;
        }
        String cnkiId = text(node, "cnki_id");
        String doi = text(node, "doi");
        String titleHash = text(node, "title_hash");
        if (StringUtils.isBlank(titleHash)) {
            titleHash = sha256Hex(normalizeTitle(title));
        }
        Integer year = intOrNull(node, "year");

        if (findExistingIdZh(cnkiId, doi, titleHash, year) != null) {
            return UpsertOutcome.DUPLICATE;
        }

        LitPaperEntity entity = new LitPaperEntity();
        entity.setCnkiId(blankToNull(cnkiId));
        entity.setDoi(blankToNull(doi));
        entity.setTitle(truncate(title, 500));
        entity.setAuthors(truncate(text(node, "authors"), 1000));
        entity.setOrgans(truncate(text(node, "organs"), 1000));
        entity.setAbstractText(text(node, "abstract_text"));
        entity.setKeywords(truncate(text(node, "keywords"), 500));
        entity.setSource(truncate(text(node, "source"), 1000));
        entity.setYear(year);
        entity.setVolume(text(node, "volume"));
        entity.setIssue(text(node, "issue"));
        entity.setPages(text(node, "pages"));
        entity.setDocType(text(node, "doc_type"));
        entity.setCiteCount(intOrNull(node, "cite_count"));
        entity.setLitSource(StringUtils.blankToDefault(text(node, "lit_source"), "CNKI"));
        entity.setCitationGbt(text(node, "citation_gbt"));
        entity.setDetailUrl(text(node, "detail_url"));
        entity.setTitleHash(titleHash);
        String crawlKeyword = text(node, "crawl_keyword");
        entity.setCrawlKeyword(StringUtils.blankToDefault(crawlKeyword, fallbackKeyword));
        entity.setStatus(StringUtils.blankToDefault(text(node, "status"), "active"));
        Date now = new Date();
        entity.setCrawledAt(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        litPaperMapper.insert(entity);
        return UpsertOutcome.IMPORTED;
    }

    private UpsertOutcome upsertOneEnOutcome(JsonNode node, String fallbackKeyword) {
        String title = text(node, "title");
        if (StringUtils.isBlank(title)) {
            return UpsertOutcome.BLANK_TITLE;
        }
        String cnkiId = text(node, "cnki_id");
        String doi = text(node, "doi");
        String titleHash = text(node, "title_hash");
        if (StringUtils.isBlank(titleHash)) {
            titleHash = sha256Hex(normalizeTitle(title));
        }
        Integer year = intOrNull(node, "year");

        if (findExistingIdEn(cnkiId, doi, titleHash, year) != null) {
            return UpsertOutcome.DUPLICATE;
        }

        LitPaperEnEntity entity = new LitPaperEnEntity();
        entity.setCnkiId(blankToNull(cnkiId));
        entity.setDoi(blankToNull(doi));
        entity.setTitle(truncate(title, 500));
        entity.setAuthors(truncate(text(node, "authors"), 1000));
        entity.setOrgans(truncate(text(node, "organs"), 1000));
        entity.setAbstractText(text(node, "abstract_text"));
        entity.setKeywords(truncate(text(node, "keywords"), 500));
        entity.setTitleZh(truncate(text(node, "title_zh"), 500));
        entity.setAbstractZh(text(node, "abstract_zh"));
        entity.setKeywordsZh(truncate(text(node, "keywords_zh"), 500));
        entity.setSource(truncate(text(node, "source"), 1000));
        entity.setYear(year);
        entity.setVolume(text(node, "volume"));
        entity.setIssue(text(node, "issue"));
        entity.setPages(text(node, "pages"));
        entity.setDocType(text(node, "doc_type"));
        entity.setCiteCount(intOrNull(node, "cite_count"));
        entity.setLitSource(StringUtils.blankToDefault(text(node, "lit_source"), "CNKI"));
        entity.setCitationGbt(text(node, "citation_gbt"));
        entity.setDetailUrl(text(node, "detail_url"));
        entity.setTitleHash(titleHash);
        String crawlKeyword = text(node, "crawl_keyword");
        entity.setCrawlKeyword(StringUtils.blankToDefault(crawlKeyword, fallbackKeyword));
        entity.setStatus(StringUtils.blankToDefault(text(node, "status"), "active"));
        Date now = new Date();
        entity.setCrawledAt(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        litPaperEnMapper.insert(entity);
        return UpsertOutcome.IMPORTED;
    }

    private Long findExistingIdZh(String cnkiId, String doi, String titleHash, Integer year) {
        if (StringUtils.isNotBlank(cnkiId)) {
            LitPaperEntity hit = litPaperMapper.selectOne(new LambdaQueryWrapper<LitPaperEntity>()
                .eq(LitPaperEntity::getCnkiId, cnkiId)
                .last("LIMIT 1"));
            if (hit != null) {
                return hit.getId();
            }
        }
        if (StringUtils.isNotBlank(doi)) {
            LitPaperEntity hit = litPaperMapper.selectOne(new LambdaQueryWrapper<LitPaperEntity>()
                .eq(LitPaperEntity::getDoi, doi)
                .last("LIMIT 1"));
            if (hit != null) {
                return hit.getId();
            }
        }
        if (StringUtils.isNotBlank(titleHash)) {
            LambdaQueryWrapper<LitPaperEntity> qw = new LambdaQueryWrapper<LitPaperEntity>()
                .eq(LitPaperEntity::getTitleHash, titleHash);
            if (year != null) {
                qw.eq(LitPaperEntity::getYear, year);
            } else {
                qw.isNull(LitPaperEntity::getYear);
            }
            LitPaperEntity hit = litPaperMapper.selectOne(qw.last("LIMIT 1"));
            if (hit != null) {
                return hit.getId();
            }
        }
        return null;
    }

    private Long findExistingIdEn(String cnkiId, String doi, String titleHash, Integer year) {
        if (StringUtils.isNotBlank(cnkiId)) {
            LitPaperEnEntity hit = litPaperEnMapper.selectOne(new LambdaQueryWrapper<LitPaperEnEntity>()
                .eq(LitPaperEnEntity::getCnkiId, cnkiId)
                .last("LIMIT 1"));
            if (hit != null) {
                return hit.getId();
            }
        }
        if (StringUtils.isNotBlank(doi)) {
            LitPaperEnEntity hit = litPaperEnMapper.selectOne(new LambdaQueryWrapper<LitPaperEnEntity>()
                .eq(LitPaperEnEntity::getDoi, doi)
                .last("LIMIT 1"));
            if (hit != null) {
                return hit.getId();
            }
        }
        if (StringUtils.isNotBlank(titleHash)) {
            LambdaQueryWrapper<LitPaperEnEntity> qw = new LambdaQueryWrapper<LitPaperEnEntity>()
                .eq(LitPaperEnEntity::getTitleHash, titleHash);
            if (year != null) {
                qw.eq(LitPaperEnEntity::getYear, year);
            } else {
                qw.isNull(LitPaperEnEntity::getYear);
            }
            LitPaperEnEntity hit = litPaperEnMapper.selectOne(qw.last("LIMIT 1"));
            if (hit != null) {
                return hit.getId();
            }
        }
        return null;
    }

    private LitOnDemandStatusVo toVo(LitOnDemandTask task) {
        LitOnDemandStatusVo vo = new LitOnDemandStatusVo();
        vo.setTaskId(task.getTaskId());
        vo.setSessionId(task.getSessionId());
        vo.setTitle(task.getTitle());
        vo.setOutlineStatus(task.getOutlineStatus());
        vo.setLitStatus(task.getLitStatus());
        vo.setKeywords(task.getKeywords());
        vo.setFetchedCount(task.getFetchedCount());
        vo.setFetchedCountZh(task.getFetchedCountZh());
        vo.setFetchedCountEn(task.getFetchedCountEn());
        vo.setSource(task.getSource());
        vo.setSelectedCountZh(task.getSelectedCountZh());
        vo.setSelectedCountEn(task.getSelectedCountEn());
        vo.setError(task.getError());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private void touch(LitOnDemandTask task) {
        task.setUpdatedAt(Instant.now());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.isBlank(text) ? null : text.trim();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            if (value != null && value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
        return value.intValue();
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String normalizeTitle(String title) {
        return title.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
