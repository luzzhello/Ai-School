package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GB/T 7714 式引文：一律英文半角标点，紧凑无多余空格（与爬虫 gbt7714.py 对齐）。
 */
public final class Gbt7714Formatter {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern DOCTYPE_TAG = Pattern.compile("\\[\\s*([A-Za-z])\\s*\\]");
    private static final Pattern DOI_PREFIX = Pattern.compile("(?i)\\bDOI\\s*:\\s*");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern BOOK_MARKS = Pattern.compile("[《》〈〉]");
    private static final Pattern SPACE_AROUND_PUNCT = Pattern.compile("\\s*([.,:;\\[\\]()])\\s*");
    private static final Pattern SPACE_AROUND_HYPHEN = Pattern.compile("(\\d)\\s*-\\s*(\\d)");
    private static final Pattern DOI_SPACE_AFTER = Pattern.compile("(?i)DOI:\\s+");

    private static final String[] PLACEHOLDER_CITES = {
        "[J].", "[D].", "[M].", "[C].", "[P].", "[S]."
    };

    private Gbt7714Formatter() {
    }

    public static String normalizeCitation(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String s = text.trim()
            .replace('，', ',')
            .replace('。', '.')
            .replace('：', ':')
            .replace('；', ';')
            .replace('（', '(')
            .replace('）', ')')
            .replace('【', '[')
            .replace('】', ']')
            .replace('［', '[')
            .replace('］', ']')
            .replace('－', '-')
            .replace('—', '-')
            .replace('–', '-')
            .replace('～', '-')
            .replace('　', ' ');
        // 入库/展示用题录不应自带列表序号，避免导出时 [1][1]
        while (true) {
            Matcher idx = Pattern.compile("^\\[\\s*\\d+\\s*]\\s*").matcher(s);
            if (!idx.find()) {
                break;
            }
            s = s.substring(idx.end()).trim();
        }
        s = BOOK_MARKS.matcher(s).replaceAll("");
        s = s.replace('\r', ' ').replace('\n', ' ');
        s = DOI_PREFIX.matcher(s).replaceAll("DOI:");
        Matcher tag = DOCTYPE_TAG.matcher(s);
        StringBuilder tagBuf = new StringBuilder();
        while (tag.find()) {
            tag.appendReplacement(tagBuf, "[" + tag.group(1).toUpperCase() + "]");
        }
        tag.appendTail(tagBuf);
        s = tagBuf.toString();
        s = SPACE_AROUND_HYPHEN.matcher(s).replaceAll("$1-$2");
        s = SPACE_AROUND_PUNCT.matcher(s).replaceAll("$1");
        s = DOI_SPACE_AFTER.matcher(s).replaceAll("DOI:");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        while (s.endsWith("..")) {
            s = s.substring(0, s.length() - 1);
        }
        if (!s.isEmpty() && !s.endsWith(".")) {
            s += ".";
        }
        return s;
    }

    public static String resolveCitation(Reference ref, String storedCitationGbt) {
        String rebuilt = formatCitation(ref);
        if (StringUtils.isNotBlank(rebuilt) && !isPlaceholder(rebuilt)) {
            return rebuilt;
        }
        if (StringUtils.isNotBlank(storedCitationGbt)) {
            return normalizeCitation(storedCitationGbt);
        }
        return rebuilt;
    }

    public static boolean isPlaceholder(String cite) {
        if (StringUtils.isBlank(cite)) {
            return true;
        }
        for (String p : PLACEHOLDER_CITES) {
            if (p.equals(cite)) {
                return true;
            }
        }
        return false;
    }

    public static String formatCitation(Reference ref) {
        String type = ref.getType() == null ? "J" : ref.getType().trim().toUpperCase();
        return switch (type) {
            case "D" -> formatThesis(ref);
            case "M" -> formatMonograph(ref);
            case "C" -> formatProceedings(ref);
            case "P" -> formatPatent(ref);
            case "S" -> formatStandard(ref);
            default -> formatJournal(ref);
        };
    }

    private static String s(String v) {
        return v == null ? "" : v.trim();
    }

    private static boolean isEnglishPaper(Reference ref) {
        String title = s(ref.getTitle());
        if (title.isEmpty()) {
            return false;
        }
        int cjk = 0;
        for (int i = 0; i < title.length(); i++) {
            if (CJK.matcher(String.valueOf(title.charAt(i))).find()) {
                cjk++;
            }
        }
        return cjk < 2;
    }

    private static String cleanTitle(String title) {
        String t = BOOK_MARKS.matcher(s(title)).replaceAll("");
        return MULTI_SPACE.matcher(t).replaceAll(" ").trim();
    }

    private static String authors(Reference ref) {
        String raw = s(ref.getAuthor());
        if (raw.isEmpty()) {
            return "";
        }
        String[] parts = raw.split("[;；、,/|]+");
        List<String> names = new ArrayList<>();
        for (String p : parts) {
            String n = MULTI_SPACE.matcher(p).replaceAll(" ").trim();
            if (!n.isEmpty()) {
                names.add(n);
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        return String.join(",", names);
    }

    private static String volIssue(Reference ref) {
        String v = s(ref.getVolume());
        String i = s(ref.getIssue());
        if (!v.isEmpty() && !i.isEmpty()) {
            return v + "(" + i + ")";
        }
        if (!v.isEmpty()) {
            return v;
        }
        if (!i.isEmpty()) {
            return "(" + i + ")";
        }
        return "";
    }

    private static String pages(String pages) {
        String p = s(pages)
            .replace('，', ',')
            .replace('：', ':')
            .replace('（', '(')
            .replace('）', ')')
            .replace('－', '-')
            .replace('—', '-')
            .replace('–', '-');
        p = SPACE_AROUND_HYPHEN.matcher(p).replaceAll("$1-$2");
        return p.replace(" ", "");
    }

    private static String doi(String doi) {
        String d = s(doi);
        if (d.isEmpty()) {
            return "";
        }
        return DOI_PREFIX.matcher(d).replaceAll("").trim();
    }

    private static String appendDoi(String body, Reference ref) {
        String d = doi(ref.getDoi());
        if (d.isEmpty()) {
            return body;
        }
        if (!body.endsWith(".")) {
            body += ".";
        }
        return body + "DOI:" + d + ".";
    }

    private static String placePublisher(String place, String publisher) {
        String p = s(place);
        String pub = s(publisher);
        if (!p.isEmpty() && !pub.isEmpty()) {
            return p + ":" + pub;
        }
        return !p.isEmpty() ? p : pub;
    }

    private static String formatJournal(Reference ref) {
        String a = authors(ref);
        String t = cleanTitle(ref.getTitle());
        String src = s(ref.getSource());
        StringBuilder sb = new StringBuilder();
        if (!a.isEmpty()) {
            sb.append(a).append('.');
        }
        sb.append(t.isEmpty() ? "[J]." : t + "[J].");
        List<String> chunks = new ArrayList<>();
        if (!src.isEmpty()) {
            chunks.add(src.endsWith(".") ? src.substring(0, src.length() - 1) : src);
        }
        if (ref.getYear() != null) {
            chunks.add(String.valueOf(ref.getYear()));
        }
        String vi = volIssue(ref);
        if (!vi.isEmpty()) {
            chunks.add(vi);
        }
        sb.append(String.join(",", chunks));
        if (StringUtils.isNotBlank(ref.getPages())) {
            sb.append(':').append(pages(ref.getPages()));
        }
        sb.append('.');
        return appendDoi(sb.toString(), ref);
    }

    private static String formatMonograph(Reference ref) {
        StringBuilder sb = new StringBuilder();
        String a = authors(ref);
        if (!a.isEmpty()) {
            sb.append(a).append('.');
        }
        String title = StringUtils.isNotBlank(ref.getTitle()) ? cleanTitle(ref.getTitle()) : s(ref.getSource());
        sb.append(title.isEmpty() ? "[M]" : title + "[M]");
        if (StringUtils.isNotBlank(ref.getTranslator())) {
            sb.append(",(").append(ref.getTranslator().trim()).append(')');
        }
        sb.append('.');
        List<String> bits = new ArrayList<>();
        String placePub = placePublisher(ref.getPublishPlace(), ref.getPublisher());
        if (!placePub.isEmpty()) {
            bits.add(placePub);
        }
        if (ref.getYear() != null) {
            bits.add(String.valueOf(ref.getYear()));
        } else if (StringUtils.isNotBlank(ref.getPublishDate())) {
            bits.add(ref.getPublishDate().trim());
        }
        if (StringUtils.isNotBlank(ref.getPages())) {
            bits.add(pages(ref.getPages()));
        }
        sb.append(String.join(",", bits)).append('.');
        return appendDoi(sb.toString(), ref);
    }

    private static String formatProceedings(Reference ref) {
        StringBuilder sb = new StringBuilder();
        String a = authors(ref);
        if (!a.isEmpty()) {
            sb.append(a).append('.');
        }
        String t = cleanTitle(ref.getTitle());
        sb.append(t.isEmpty() ? "[C]." : t + "[C].");
        if (StringUtils.isNotBlank(ref.getSource())) {
            sb.append(ref.getSource().trim()).append('.');
        }
        List<String> bits = new ArrayList<>();
        String placePub = placePublisher(ref.getPublishPlace(), ref.getPublisher());
        if (!placePub.isEmpty()) {
            bits.add(placePub);
        }
        if (ref.getYear() != null) {
            bits.add(String.valueOf(ref.getYear()));
        }
        if (StringUtils.isNotBlank(ref.getPages())) {
            bits.add(pages(ref.getPages()));
        }
        sb.append(String.join(",", bits)).append('.');
        return appendDoi(sb.toString(), ref);
    }

    private static String formatThesis(Reference ref) {
        StringBuilder sb = new StringBuilder();
        String a = authors(ref);
        if (!a.isEmpty()) {
            sb.append(a).append('.');
        }
        String t = cleanTitle(ref.getTitle());
        sb.append(t.isEmpty() ? "[D]" : t + "[D]");
        String degree = s(ref.getDegree());
        if (!degree.isEmpty()) {
            if (("硕士".equals(degree) || "博士".equals(degree) || "学士".equals(degree))
                && !degree.endsWith("学位论文")) {
                degree = degree + "学位论文";
            }
            sb.append(":[").append(degree).append(']');
        }
        sb.append('.');
        String place = StringUtils.isNotBlank(ref.getDegreePlace())
            ? ref.getDegreePlace().trim() : s(ref.getPublishPlace());
        String unit = s(ref.getSource());
        if (!place.isEmpty() && !unit.isEmpty()) {
            sb.append(place).append(':').append(unit);
        } else {
            sb.append(!unit.isEmpty() ? unit : place);
        }
        if (ref.getYear() != null) {
            sb.append(',').append(ref.getYear());
        }
        sb.append('.');
        return sb.toString();
    }

    private static String formatPatent(Reference ref) {
        StringBuilder sb = new StringBuilder();
        String a = authors(ref);
        if (!a.isEmpty()) {
            sb.append(a).append('.');
        }
        String t = cleanTitle(ref.getTitle());
        sb.append(t.isEmpty() ? "[P]." : t + "[P].");
        List<String> mid = new ArrayList<>();
        if (StringUtils.isNotBlank(ref.getPatentCountry())) {
            mid.add(ref.getPatentCountry().trim());
        }
        if (StringUtils.isNotBlank(ref.getPatentKind())) {
            mid.add(ref.getPatentKind().trim());
        }
        if (StringUtils.isNotBlank(ref.getPatentNo())) {
            mid.add(ref.getPatentNo().trim());
        }
        if (StringUtils.isNotBlank(ref.getPublishDate())) {
            mid.add(ref.getPublishDate().trim());
        } else if (ref.getYear() != null) {
            mid.add(String.valueOf(ref.getYear()));
        }
        sb.append(String.join(",", mid)).append('.');
        return sb.toString();
    }

    private static String formatStandard(Reference ref) {
        StringBuilder sb = new StringBuilder();
        String issuer = StringUtils.isNotBlank(ref.getAuthor()) ? ref.getAuthor().trim() : s(ref.getSource());
        if (!issuer.isEmpty()) {
            sb.append(issuer).append('.');
        }
        if (StringUtils.isNotBlank(ref.getStandardCode())) {
            sb.append(ref.getStandardCode().trim()).append('.');
        }
        String t = cleanTitle(ref.getTitle());
        sb.append(t.isEmpty() ? "[S]." : t + "[S].");
        String placePub = placePublisher(ref.getPublishPlace(), ref.getPublisher());
        String date = StringUtils.isNotBlank(ref.getPublishDate())
            ? ref.getPublishDate().trim()
            : (ref.getYear() != null ? String.valueOf(ref.getYear()) : "");
        if (!placePub.isEmpty() && !date.isEmpty()) {
            sb.append(placePub).append(',').append(date);
        } else if (!placePub.isEmpty()) {
            sb.append(placePub);
        } else if (!date.isEmpty()) {
            sb.append(date);
        }
        sb.append('.');
        return sb.toString();
    }
}
