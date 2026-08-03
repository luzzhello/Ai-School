package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.Reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正文文献角标后质检：剔除不在已确认参考文献列表中的 [n]/[n,m]/[n-m]。
 * <p>
 * 借鉴 nature-ref-verifier 的「序号必须可核验」原则；摘要/致谢等节可整段去掉角标。
 */
final class PaperCitationSanitizer {

    /** 匹配 [1]、[1,2]、[1，2]、[1-3] 等（不含参考文献列表中的「[J]」类型标记） */
    private static final Pattern CITATION_BRACKET = Pattern.compile("\\[(\\d+(?:\\s*[,，\\-]\\s*\\d+)*)\\]");

    private PaperCitationSanitizer() {
    }

    static Set<Integer> collectValidIndexes(List<Reference> references) {
        Set<Integer> indexes = new LinkedHashSet<>();
        if (references == null) {
            return indexes;
        }
        for (Reference ref : references) {
            if (ref != null && ref.getIndex() != null && ref.getIndex() > 0) {
                indexes.add(ref.getIndex());
            }
        }
        return indexes;
    }

    /**
     * 保留合法角标；非法序号从括号中剔除，若括号内无合法序号则整段删除。
     */
    static String sanitizeToValidIndexes(String content, Collection<Integer> validIndexes) {
        if (StringUtils.isBlank(content)) {
            return content == null ? "" : content;
        }
        if (validIndexes == null || validIndexes.isEmpty()) {
            // 尚无已确认文献时，去掉正文中的数字角标，避免编造序号残留
            return stripAllNumericCitations(content);
        }
        Matcher matcher = CITATION_BRACKET.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String rebuilt = rebuildCitation(matcher.group(1), validIndexes);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rebuilt));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 去掉全部数字角标（摘要、致谢等不应出现文献引用处） */
    static String stripAllNumericCitations(String content) {
        if (StringUtils.isBlank(content)) {
            return content == null ? "" : content;
        }
        return CITATION_BRACKET.matcher(content).replaceAll("");
    }

    private static String rebuildCitation(String inner, Collection<Integer> validIndexes) {
        List<Integer> kept = new ArrayList<>();
        String[] parts = inner.split("[,，\\-]");
        boolean isRange = inner.contains("-");
        if (isRange && parts.length == 2) {
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                if (start > end) {
                    int tmp = start;
                    start = end;
                    end = tmp;
                }
                for (int i = start; i <= end; i++) {
                    if (validIndexes.contains(i) && !kept.contains(i)) {
                        kept.add(i);
                    }
                }
            } catch (NumberFormatException ignored) {
                return "";
            }
        } else {
            for (String part : parts) {
                try {
                    int idx = Integer.parseInt(part.trim());
                    if (validIndexes.contains(idx) && !kept.contains(idx)) {
                        kept.add(idx);
                    }
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        if (kept.isEmpty()) {
            return "";
        }
        if (kept.size() == 1) {
            return "[" + kept.get(0) + "]";
        }
        // 连续区间可压缩为 [a-b]，否则用逗号
        if (isContiguous(kept)) {
            return "[" + kept.get(0) + "-" + kept.get(kept.size() - 1) + "]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(kept.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    private static boolean isContiguous(List<Integer> sortedUnique) {
        if (sortedUnique.size() < 2) {
            return false;
        }
        for (int i = 1; i < sortedUnique.size(); i++) {
            if (sortedUnique.get(i) != sortedUnique.get(i - 1) + 1) {
                return false;
            }
        }
        return true;
    }
}
