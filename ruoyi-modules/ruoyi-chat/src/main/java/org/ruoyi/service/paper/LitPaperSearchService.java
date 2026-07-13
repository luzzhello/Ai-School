package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.LitPaperProperties;
import org.ruoyi.domain.entity.lit.LitPaperEntity;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.mapper.lit.LitPaperMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 从 lit_paper 检索真实文献并映射为论文会话 Reference。
 * FULLTEXT 不足时回退 LIKE；不做 LLM 补全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LitPaperSearchService {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fa5]");

    private final LitPaperMapper litPaperMapper;
    private final LitPaperProperties litPaperProperties;

    public List<Reference> search(String keyword, String language, int limit) {
        if (StringUtils.isBlank(keyword) || limit <= 0) {
            return List.of();
        }
        int fromYear = LocalDate.now().getYear() - litPaperProperties.getRecentYears();
        int fetch = Math.max(limit * 3, limit);
        List<LitPaperEntity> rows;
        try {
            rows = litPaperMapper.searchFulltext(keyword.trim(), fromYear, fetch);
        } catch (Exception e) {
            log.warn("lit_paper FULLTEXT search failed, fallback to LIKE: {}", e.getMessage());
            rows = List.of();
        }
        if (rows == null || rows.isEmpty()) {
            rows = litPaperMapper.searchLike(keyword.trim(), fromYear, fetch);
        }
        Map<Long, Reference> uniq = new LinkedHashMap<>();
        for (LitPaperEntity row : rows) {
            Reference ref = toReference(row);
            if (language != null && !language.equals(ref.getLanguage())) {
                continue;
            }
            uniq.putIfAbsent(row.getId(), ref);
            if (uniq.size() >= limit) {
                break;
            }
        }
        List<Reference> list = new ArrayList<>(uniq.values());
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setIndex(i + 1);
        }
        return list;
    }

    private Reference toReference(LitPaperEntity row) {
        Reference ref = new Reference();
        ref.setAuthor(row.getAuthors());
        ref.setTitle(row.getTitle());
        ref.setSource(row.getSource());
        ref.setYear(row.getYear());
        ref.setDoi(row.getDoi());
        ref.setType(StringUtils.isNotBlank(row.getDocType()) ? row.getDocType() : "J");
        ref.setAbstractText(row.getAbstractText());
        ref.setLanguage(detectLanguage(ref));
        if (StringUtils.isNotBlank(row.getCitationGbt())) {
            ref.setCitation(row.getCitationGbt());
        } else {
            ref.setCitation(formatCitation(ref));
        }
        return ref;
    }

    private String detectLanguage(Reference ref) {
        String probe = (ref.getTitle() == null ? "" : ref.getTitle())
            + (ref.getAuthor() == null ? "" : ref.getAuthor());
        return CJK.matcher(probe).find() ? "zh" : "en";
    }

    private String formatCitation(Reference ref) {
        String type = ref.getType() == null ? "" : ref.getType().trim().toUpperCase();
        String tag = switch (type) {
            case "D" -> "[D]";
            case "M" -> "[M]";
            case "C" -> "[C]";
            default -> "[J]";
        };
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(ref.getAuthor())) {
            sb.append(ref.getAuthor().trim()).append('.');
        }
        if (StringUtils.isNotBlank(ref.getTitle())) {
            sb.append(ref.getTitle().trim());
        }
        sb.append(tag);
        if (StringUtils.isNotBlank(ref.getSource())) {
            sb.append(ref.getSource().trim());
        }
        if (ref.getYear() != null) {
            sb.append(',').append(ref.getYear());
        }
        sb.append('.');
        if (StringUtils.isNotBlank(ref.getDoi())) {
            sb.append("DOI:").append(ref.getDoi().trim()).append('.');
        }
        return sb.toString();
    }
}
