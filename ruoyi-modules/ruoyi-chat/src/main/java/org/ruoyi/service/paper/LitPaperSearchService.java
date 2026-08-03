package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.LitPaperProperties;
import org.ruoyi.domain.entity.lit.LitPaperEntity;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.mapper.lit.LitPaperEnMapper;
import org.ruoyi.mapper.lit.LitPaperMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文献库检索：中文查 {@code lit_paper}，英文查 {@code lit_paper_en}；不做「全部」混合检索。
 * <p>
 * 检索策略：先将题目/关键词分词，每个词各查 {@code searchPerKeyword} 条，再合并去重，
 * 总数不超过 {@code searchMaxTotal}（默认 10 / 50）。
 * <p>
 * 英文库检索：若关键词含中文（用户常用中文题检索外文文献），
 * <b>一律匹配知网中译字段</b> {@code title_zh/keywords_zh/abstract_zh}；
 * 纯英文关键词再匹配原文 title/keywords/abstract。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LitPaperSearchService {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern TOKEN_SPLIT = Pattern.compile("\\s+");

    private final LitPaperMapper litPaperMapper;
    private final LitPaperEnMapper litPaperEnMapper;
    private final LitPaperProperties litPaperProperties;

    /**
     * @param language 仅支持 {@code zh} / {@code en}；其它值抛错
     * @param limit    期望条数上限（会再与 {@code searchMaxTotal} 取小）
     */
    public List<Reference> search(String keyword, String language, int limit) {
        if (StringUtils.isBlank(keyword) || limit <= 0) {
            return List.of();
        }
        String lang = normalizeLanguage(language);
        int perKeyword = Math.max(1, litPaperProperties.getSearchPerKeyword());
        int maxTotal = Math.max(1, litPaperProperties.getSearchMaxTotal());
        int effectiveLimit = Math.min(limit, maxTotal);

        LitPaperProperties.OnDemand ondemand = litPaperProperties.getOndemand();
        int minKw = ondemand != null ? ondemand.getMinKeywords() : 3;
        int maxKw = ondemand != null ? ondemand.getMaxKeywords() : 5;
        List<String> tokens = TitleKeywordSplitter.split(keyword.trim(), minKw, maxKw);
        if (tokens.isEmpty()) {
            tokens = List.of(keyword.trim());
        }

        log.info("lit search lang={} keyword='{}' tokens={} perKeyword={} limit={}",
            lang, keyword.trim(), tokens, perKeyword, effectiveLimit);

        int fromYear = LocalDate.now().getYear() - litPaperProperties.getRecentYears();
        Map<Long, Reference> uniq = new LinkedHashMap<>();
        for (String token : tokens) {
            if (StringUtils.isBlank(token) || uniq.size() >= effectiveLimit) {
                break;
            }
            List<LitPaperEntity> rows = searchBySingleKeyword(token, lang, fromYear, perKeyword);
            for (LitPaperEntity row : rows) {
                uniq.putIfAbsent(row.getId(), toReference(row, lang));
                if (uniq.size() >= effectiveLimit) {
                    break;
                }
            }
        }

        List<Reference> list = new ArrayList<>(uniq.values());
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setIndex(i + 1);
        }
        return list;
    }

    public static String normalizeLanguage(String language) {
        if (StringUtils.isBlank(language)) {
            throw new ServiceException("请选择中文或英文文献");
        }
        String lang = language.trim().toLowerCase();
        if ("zh".equals(lang) || "en".equals(lang)) {
            return lang;
        }
        throw new ServiceException("文献语言仅支持中文(zh)或英文(en)");
    }

    /**
     * 单关键词检索（不做二次分词），最多返回 {@code limit} 条。
     */
    private List<LitPaperEntity> searchBySingleKeyword(String keyword, String lang, int fromYear, int limit) {
        String original = keyword.trim();
        String query = LitQueryNormalizer.toSearchQuery(original);
        if (StringUtils.isBlank(query)) {
            query = original;
        }

        // 英文库 + 中文检索词：强制走 *_zh（用原文判断，避免清洗后只剩英文专名）
        boolean enZh = "en".equals(lang) && containsCjk(original);
        List<LitPaperEntity> rows = enZh
            ? searchEnZh(query, fromYear, limit)
            : searchRows(lang, query, fromYear, limit);
        if ((rows == null || rows.isEmpty()) && !query.equals(original)) {
            rows = enZh
                ? searchEnZh(original, fromYear, limit)
                : searchRows(lang, original, fromYear, limit);
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (rows.size() <= limit) {
            return rows;
        }
        return new ArrayList<>(rows.subList(0, limit));
    }

    private List<LitPaperEntity> searchRows(String lang, String query, int fromYear, int fetch) {
        if ("en".equals(lang)) {
            return searchEn(query, fromYear, fetch);
        }
        return searchZh(query, fromYear, fetch);
    }

    private List<LitPaperEntity> searchZh(String query, int fromYear, int fetch) {
        try {
            List<LitPaperEntity> rows = litPaperMapper.searchFulltext(query, fromYear, fetch);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            log.warn("lit_paper FULLTEXT search failed, fallback to LIKE: {}", e.getMessage());
        }
        try {
            return litPaperMapper.searchLike(query, fromYear, fetch);
        } catch (Exception e) {
            log.warn("lit_paper LIKE search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<LitPaperEntity> searchEn(String query, int fromYear, int fetch) {
        if (containsCjk(query)) {
            return searchEnZh(query, fromYear, fetch);
        }
        try {
            List<LitPaperEntity> rows = litPaperEnMapper.searchFulltext(query, fromYear, fetch);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            log.warn("lit_paper_en FULLTEXT search failed, fallback to LIKE: {}", e.getMessage());
        }
        try {
            return litPaperEnMapper.searchLike(query, fromYear, fetch);
        } catch (Exception e) {
            log.warn("lit_paper_en LIKE search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 英文表中译字段检索：FULLTEXT → 整句 LIKE → 按词分拆 LIKE 合并。
     */
    private List<LitPaperEntity> searchEnZh(String query, int fromYear, int fetch) {
        try {
            List<LitPaperEntity> rows = litPaperEnMapper.searchFulltextZh(query, fromYear, fetch);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            log.warn("lit_paper_en ZH FULLTEXT search failed, fallback to LIKE: {}", e.getMessage());
        }
        try {
            List<LitPaperEntity> rows = litPaperEnMapper.searchLikeZh(query, fromYear, fetch);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            log.warn("lit_paper_en ZH LIKE search failed: {}", e.getMessage());
        }
        // 多词整句 LIKE 难命中：逐词查中译字段再合并
        List<LitPaperEntity> merged = mergeLikeZhByTokens(query, fromYear, fetch);
        if (!merged.isEmpty()) {
            return merged;
        }
        // 最后兜底：英文专名可能仍在原文 title 中
        try {
            return litPaperEnMapper.searchLike(query, fromYear, fetch);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<LitPaperEntity> mergeLikeZhByTokens(String query, int fromYear, int fetch) {
        Map<Long, LitPaperEntity> uniq = new LinkedHashMap<>();
        for (String token : TOKEN_SPLIT.split(query.trim())) {
            String t = token.trim();
            if (t.length() < 2) {
                continue;
            }
            // 去掉粘在英文后的「的」前缀：的骑行网站 → 骑行网站
            if (t.startsWith("的") && t.length() > 2 && containsCjk(t.substring(1))) {
                t = t.substring(1);
            }
            try {
                List<LitPaperEntity> part = litPaperEnMapper.searchLikeZh(t, fromYear, fetch);
                if (part == null) {
                    continue;
                }
                for (LitPaperEntity row : part) {
                    uniq.putIfAbsent(row.getId(), row);
                    if (uniq.size() >= fetch) {
                        return new ArrayList<>(uniq.values());
                    }
                }
            } catch (Exception ignored) {
                // continue other tokens
            }
        }
        return new ArrayList<>(uniq.values());
    }

    private static boolean containsCjk(String text) {
        return text != null && CJK.matcher(text).find();
    }

    private Reference toReference(LitPaperEntity row, String lang) {
        Reference ref = new Reference();
        ref.setAuthor(row.getAuthors());
        ref.setTitle(row.getTitle());
        ref.setSource(row.getSource());
        ref.setYear(row.getYear());
        ref.setDoi(row.getDoi());
        ref.setType(StringUtils.isNotBlank(row.getDocType()) ? row.getDocType() : "J");
        ref.setVolume(row.getVolume());
        ref.setIssue(row.getIssue());
        ref.setPages(row.getPages());
        ref.setPublisher(row.getPublisher());
        ref.setPublishPlace(row.getPublishPlace());
        ref.setTranslator(row.getTranslator());
        ref.setDegree(row.getDegree());
        ref.setDegreePlace(row.getDegreePlace());
        ref.setPatentCountry(row.getPatentCountry());
        ref.setPatentKind(row.getPatentKind());
        ref.setPatentNo(row.getPatentNo());
        ref.setStandardCode(row.getStandardCode());
        ref.setPublishDate(row.getPublishDate());
        ref.setAbstractText(row.getAbstractText());
        ref.setDetailUrl(row.getDetailUrl());
        ref.setLanguage(lang);
        ref.setCitation(Gbt7714Formatter.resolveCitation(ref, row.getCitationGbt()));
        return ref;
    }
}
