package org.ruoyi.service.draw.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.AigcDetectRequest;
import org.ruoyi.domain.dto.request.AigcDetectSegmentRequest;
import org.ruoyi.domain.entity.draw.AigcDetectReport;
import org.ruoyi.domain.vo.draw.AigcDetectChunkVo;
import org.ruoyi.domain.vo.draw.AigcDetectReportDetailVo;
import org.ruoyi.domain.vo.draw.AigcDetectReportSummaryVo;
import org.ruoyi.domain.vo.draw.AigcDetectResultVo;
import org.ruoyi.domain.vo.draw.AigcDetectSegmentResultVo;
import org.ruoyi.mapper.draw.AigcDetectReportMapper;
import org.ruoyi.service.draw.IAigcDetectService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AigcDetectServiceImpl implements IAigcDetectService {

    private static final int MIN_DETECT_CHARS = 20;
    private static final int MAX_SECTIONS = 120;
    private static final int DETECT_CONCURRENCY = 6;
    private static final int DEFAULT_REPORT_LIMIT = 50;
    private static final int MAX_REPORT_LIMIT = 100;

    private final IFeatureCoinService featureCoinService;
    private final AigcLlmSupport aigcLlmSupport;
    private final AigcDetectReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AigcDetectResultVo detect(AigcDetectRequest request) {
        String title = StringUtils.trim(request.getTitle());
        String content = StringUtils.trim(request.getContent());
        if (StringUtils.isBlank(title)) {
            throw new ServiceException("论文标题不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("论文内容不能为空");
        }
        if (content.length() < 50) {
            throw new ServiceException("论文内容过短，请至少输入 50 字");
        }

        List<AigcOutlineSegmenter.OutlinePart> allUnits = AigcOutlineSegmenter.splitOutlineUnits(content);
        List<AigcOutlineSegmenter.OutlinePart> sections = allUnits.stream()
            .filter(AigcOutlineSegmenter::isDetectableBody)
            .toList();
        if (sections.isEmpty()) {
            throw new ServiceException("未能识别到可检测的章节正文。请确认论文含一/二/三级标题，"
                + "且正文不仅是目录或参考文献");
        }
        if (sections.size() > MAX_SECTIONS) {
            throw new ServiceException("章节过多（超过 " + MAX_SECTIONS
                + " 个），请删减目录后重试，或仅粘贴需检测的章节正文");
        }

        // 计费按实际进入检测的正文字数（不含目录/标题行/参考文献）
        int wordCount = sections.stream()
            .mapToInt(p -> AigcTextSegmenter.countWords(p.body()))
            .sum();
        if (wordCount < 50) {
            throw new ServiceException("可检测正文过短（目录与参考文献已排除），请至少保留 50 字章节正文");
        }
        Long userId = LoginHelper.getUserId();
        featureCoinService.requireAffordable(userId, FeatureCodes.AIGC_DETECT, wordCount);

        List<AigcDetectChunkVo> detected = detectSections(sections);
        // 按全文大纲顺序组装：未检测章节灰色展示，检测正文带风险色
        List<AigcDetectChunkVo> segments = mergeDisplaySegments(allUnits, detected);
        double aigcRate = weightedRate(detected);
        double humanRate = Math.max(0, Math.min(100, 100 - aigcRate));
        long highRiskCount = detected.stream().filter(s -> "high".equals(s.getRiskLevel())).count();
        int highRiskWords = detected.stream()
            .filter(s -> "high".equals(s.getRiskLevel()))
            .mapToInt(s -> s.getWordCount() == null ? 0 : s.getWordCount())
            .sum();

        AigcDetectResultVo vo = new AigcDetectResultVo();
        vo.setTitle(title);
        vo.setWordCount(wordCount);
        vo.setAigcRate(aigcRate);
        vo.setHumanRate(humanRate);
        vo.setSegments(segments);
        long cost = featureCoinService.charge(userId, FeatureCodes.AIGC_DETECT, wordCount);
        vo.setCostCoins((int) cost);
        vo.setSummary(buildSummary(aigcRate, wordCount, detected.size(), highRiskCount, highRiskWords));

        String reportId = null;
        try {
            reportId = saveReport(userId, request.getMode(), content, vo);
        }
        catch (Exception e) {
            log.warn("AIGC 检测报告自动保存失败（不影响本次结果）: {}", e.getMessage());
        }
        vo.setReportId(reportId);
        return vo;
    }

    @Override
    public AigcDetectSegmentResultVo detectSegment(AigcDetectSegmentRequest request) {
        String text = StringUtils.trim(request.getText());
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("片段内容不能为空");
        }
        int wordCount = AigcTextSegmenter.countWords(text);
        Long userId = LoginHelper.getUserId();
        featureCoinService.requireAffordable(userId, FeatureCodes.AIGC_DETECT, wordCount);

        double aigcRate = aigcLlmSupport.detectAigcRate(text, request.getModel());
        AigcDetectSegmentResultVo vo = new AigcDetectSegmentResultVo();
        vo.setAigcRate(aigcRate);
        vo.setHumanRate(Math.max(0, Math.min(100, 100 - aigcRate)));
        long cost = featureCoinService.charge(userId, FeatureCodes.AIGC_DETECT, wordCount);
        vo.setCostCoins((int) cost);
        return vo;
    }

    @Override
    public List<AigcDetectReportSummaryVo> listReports(Integer limit) {
        Long userId = LoginHelper.getUserId();
        int size = limit == null ? DEFAULT_REPORT_LIMIT : Math.min(Math.max(limit, 1), MAX_REPORT_LIMIT);
        List<AigcDetectReport> rows = reportMapper.selectList(Wrappers.<AigcDetectReport>lambdaQuery()
            .eq(AigcDetectReport::getUserId, userId)
            .orderByDesc(AigcDetectReport::getCreateTime)
            .last("LIMIT " + size)
            .select(
                AigcDetectReport::getReportId,
                AigcDetectReport::getTitle,
                AigcDetectReport::getWordCount,
                AigcDetectReport::getAigcRate,
                AigcDetectReport::getHumanRate,
                AigcDetectReport::getCostCoins,
                AigcDetectReport::getSummary,
                AigcDetectReport::getCreateTime
            ));
        List<AigcDetectReportSummaryVo> list = new ArrayList<>(rows.size());
        for (AigcDetectReport row : rows) {
            list.add(toSummary(row));
        }
        return list;
    }

    @Override
    public AigcDetectReportDetailVo getReport(String reportId) {
        Long userId = LoginHelper.getUserId();
        AigcDetectReport row = requireOwnedReport(reportId, userId);
        return toDetail(row);
    }

    @Override
    public void deleteReport(String reportId) {
        Long userId = LoginHelper.getUserId();
        AigcDetectReport row = requireOwnedReport(reportId, userId);
        reportMapper.deleteById(row.getId());
    }

    private String saveReport(Long userId, String inputMode, String content, AigcDetectResultVo vo) {
        String reportId = UUID.randomUUID().toString().replace("-", "");
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("title", vo.getTitle());
            payload.put("content", content);
            payload.put("wordCount", vo.getWordCount());
            payload.put("aigcRate", vo.getAigcRate());
            payload.put("humanRate", vo.getHumanRate());
            payload.put("costCoins", vo.getCostCoins());
            payload.put("summary", vo.getSummary());
            payload.set("segments", objectMapper.valueToTree(vo.getSegments()));

            Date now = new Date();
            AigcDetectReport row = new AigcDetectReport();
            row.setReportId(reportId);
            row.setUserId(userId);
            row.setTitle(StringUtils.substring(vo.getTitle(), 0, 200));
            row.setWordCount(vo.getWordCount());
            row.setAigcRate(toDecimal(vo.getAigcRate()));
            row.setHumanRate(toDecimal(vo.getHumanRate()));
            row.setCostCoins(vo.getCostCoins());
            row.setSummary(StringUtils.substring(vo.getSummary(), 0, 500));
            row.setInputMode(StringUtils.blankToDefault(StringUtils.trim(inputMode), "text"));
            row.setResultJson(objectMapper.writeValueAsString(payload));
            row.setCreateTime(now);
            row.setUpdateTime(now);
            reportMapper.insert(row);
            return reportId;
        }
        catch (ServiceException e) {
            throw e;
        }
        catch (Exception e) {
            log.warn("保存 AIGC 检测报告失败: {}", e.getMessage());
            throw new ServiceException("检测报告保存失败");
        }
    }

    private AigcDetectReport requireOwnedReport(String reportId, Long userId) {
        if (StringUtils.isBlank(reportId)) {
            throw new ServiceException("报告 ID 无效");
        }
        AigcDetectReport row = reportMapper.selectOne(Wrappers.<AigcDetectReport>lambdaQuery()
            .eq(AigcDetectReport::getReportId, reportId)
            .eq(AigcDetectReport::getUserId, userId));
        if (row == null) {
            throw new ServiceException("报告不存在或无权访问");
        }
        return row;
    }

    private AigcDetectReportSummaryVo toSummary(AigcDetectReport row) {
        AigcDetectReportSummaryVo vo = new AigcDetectReportSummaryVo();
        vo.setReportId(row.getReportId());
        vo.setTitle(row.getTitle());
        vo.setWordCount(row.getWordCount());
        vo.setAigcRate(toDouble(row.getAigcRate()));
        vo.setHumanRate(toDouble(row.getHumanRate()));
        vo.setCostCoins(row.getCostCoins());
        vo.setSummary(row.getSummary());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    private AigcDetectReportDetailVo toDetail(AigcDetectReport row) {
        AigcDetectReportDetailVo vo = new AigcDetectReportDetailVo();
        vo.setReportId(row.getReportId());
        vo.setTitle(row.getTitle());
        vo.setWordCount(row.getWordCount());
        vo.setAigcRate(toDouble(row.getAigcRate()));
        vo.setHumanRate(toDouble(row.getHumanRate()));
        vo.setCostCoins(row.getCostCoins());
        vo.setSummary(row.getSummary());
        vo.setCreateTime(row.getCreateTime());
        try {
            JsonNode root = objectMapper.readTree(row.getResultJson());
            if (root.hasNonNull("content")) {
                vo.setContent(root.get("content").asText());
            }
            if (root.has("segments") && root.get("segments").isArray()) {
                List<AigcDetectChunkVo> segments = objectMapper.convertValue(
                    root.get("segments"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AigcDetectChunkVo.class)
                );
                vo.setSegments(segments != null ? segments : List.of());
            }
            if (StringUtils.isBlank(vo.getSummary()) && root.hasNonNull("summary")) {
                vo.setSummary(root.get("summary").asText());
            }
        }
        catch (Exception e) {
            log.warn("解析 AIGC 报告 JSON 失败: {}", e.getMessage());
            throw new ServiceException("报告内容损坏，无法打开");
        }
        return vo;
    }

    private List<AigcDetectChunkVo> detectSections(List<AigcOutlineSegmenter.OutlinePart> sections) {
        int concurrency = Math.min(DETECT_CONCURRENCY, Math.max(1, sections.size()));
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<CompletableFuture<AigcDetectChunkVo>> futures = new ArrayList<>(sections.size());
            for (int i = 0; i < sections.size(); i++) {
                final int index = i;
                final AigcOutlineSegmenter.OutlinePart part = sections.get(i);
                futures.add(CompletableFuture.supplyAsync(() -> detectOneSection(index, part), pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<AigcDetectChunkVo> result = new ArrayList<>(sections.size());
            for (CompletableFuture<AigcDetectChunkVo> future : futures) {
                result.add(future.join());
            }
            result.sort(Comparator.comparing(AigcDetectChunkVo::getIndex));
            return result;
        }
        catch (ServiceException e) {
            throw e;
        }
        catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ServiceException serviceException) {
                throw serviceException;
            }
            log.warn("章节级 AIGC 检测失败: {}", cause.getMessage());
            throw new ServiceException("检测失败，请稍后重试");
        }
        finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(120, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            }
            catch (InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private AigcDetectChunkVo detectOneSection(int index, AigcOutlineSegmenter.OutlinePart part) {
        try {
            String text = part.body();
            int words = AigcTextSegmenter.countWords(text);
            AigcDetectChunkVo chunk = new AigcDetectChunkVo();
            chunk.setIndex(index);
            chunk.setHeading(part.title());
            chunk.setLevel(part.level());
            chunk.setText(text);
            chunk.setWordCount(words);
            chunk.setSkipped(false);
            // 过短正文不调用模型，记为无风险
            if (words < MIN_DETECT_CHARS) {
                chunk.setAigcRate(0D);
                chunk.setRiskLevel("none");
                return chunk;
            }
            double rate = aigcLlmSupport.detectAigcRate(text, null);
            chunk.setAigcRate(rate);
            chunk.setRiskLevel(resolveRiskLevel(rate));
            return chunk;
        }
        catch (ServiceException e) {
            throw e;
        }
        catch (Exception e) {
            log.warn("章节 {} 检测失败: {}", index, e.getMessage());
            throw new ServiceException("章节检测失败，请稍后重试");
        }
    }

    /**
     * 按大纲全文顺序组装展示段：未检测章节（目录/参考文献/仅标题）标 skipped，
     * 可检测正文写入风险结果，便于前端连续原文着色。
     */
    private static List<AigcDetectChunkVo> mergeDisplaySegments(
        List<AigcOutlineSegmenter.OutlinePart> allUnits,
        List<AigcDetectChunkVo> detected
    ) {
        java.util.Map<String, AigcDetectChunkVo> byKey = new java.util.HashMap<>();
        for (AigcDetectChunkVo chunk : detected) {
            byKey.put(sectionKey(chunk.getHeading(), chunk.getText()), chunk);
        }
        List<AigcDetectChunkVo> merged = new ArrayList<>(allUnits.size());
        int index = 0;
        for (AigcOutlineSegmenter.OutlinePart part : allUnits) {
            AigcDetectChunkVo chunk = new AigcDetectChunkVo();
            chunk.setIndex(index++);
            chunk.setHeading(part.title());
            chunk.setLevel(part.level());
            chunk.setText(StringUtils.defaultString(part.body()));
            chunk.setWordCount(AigcTextSegmenter.countWords(part.body()));
            if (AigcOutlineSegmenter.isDetectableBody(part)) {
                AigcDetectChunkVo hit = byKey.get(sectionKey(part.title(), part.body()));
                if (hit != null) {
                    chunk.setAigcRate(hit.getAigcRate());
                    chunk.setRiskLevel(hit.getRiskLevel());
                    chunk.setWordCount(hit.getWordCount());
                    chunk.setSkipped(false);
                }
                else {
                    chunk.setAigcRate(0D);
                    chunk.setRiskLevel("none");
                    chunk.setSkipped(false);
                }
            }
            else {
                chunk.setAigcRate(null);
                chunk.setRiskLevel("skip");
                chunk.setSkipped(true);
            }
            merged.add(chunk);
        }
        return merged;
    }

    private static String sectionKey(String heading, String body) {
        return StringUtils.defaultString(heading) + "\u0001" + StringUtils.defaultString(body);
    }

    private static double weightedRate(List<AigcDetectChunkVo> segments) {
        double weightedSum = 0;
        int totalWords = 0;
        for (AigcDetectChunkVo segment : segments) {
            if (Boolean.TRUE.equals(segment.getSkipped())) {
                continue;
            }
            int words = segment.getWordCount() == null ? 0 : segment.getWordCount();
            double rate = segment.getAigcRate() == null ? 0 : segment.getAigcRate();
            weightedSum += rate * words;
            totalWords += words;
        }
        if (totalWords <= 0) {
            return 0;
        }
        return Math.round((weightedSum / totalWords) * 10.0) / 10.0;
    }

    /**
     * 与产品图例对齐：
     * none=0% 无风险；low=&gt;0~20% 低风险；mid=21~50% 中风险；high=51%+ 高风险
     */
    private static String resolveRiskLevel(double aigcRate) {
        if (aigcRate <= 0) {
            return "none";
        }
        if (aigcRate <= 20) {
            return "low";
        }
        if (aigcRate <= 50) {
            return "mid";
        }
        return "high";
    }

    private String buildSummary(double aigcRate, int wordCount, int chunkCount, long highRiskCount, int highRiskWords) {
        if (aigcRate >= 51) {
            return String.format("按章节标题检测共 %d 段正文，约 %d 字（已排除目录/参考文献）；全文参考 AIGC %.1f%%，高风险 %d 段（约 %d 字），建议优先改写高风险章节。",
                chunkCount, wordCount, aigcRate, highRiskCount, highRiskWords);
        }
        if (aigcRate >= 21) {
            return String.format("按章节标题检测共 %d 段正文，约 %d 字（已排除目录/参考文献）；全文参考 AIGC %.1f%%，高风险 %d 段，可针对中高风险章节改写。",
                chunkCount, wordCount, aigcRate, highRiskCount);
        }
        return String.format("按章节标题检测共 %d 段正文，约 %d 字（已排除目录/参考文献）；全文参考 AIGC %.1f%%，整体风险偏低。",
            chunkCount, wordCount, aigcRate);
    }

    private static BigDecimal toDecimal(Double value) {
        double v = value == null ? 0 : value;
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }
}
