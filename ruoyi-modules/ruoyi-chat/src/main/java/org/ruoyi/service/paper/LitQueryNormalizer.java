package org.ruoyi.service.paper;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.WordDictionary;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将论文题目清洗为文献库检索词。
 * <p>
 * 例：{@code 基于Springboot+vue的骑行网站设计与实现}
 * → {@code Springboot vue 骑行 网站设计}
 * <p>
 * 中文以领域词典前向最长匹配为主（稳定）；jieba 可用时仅作补充，
 * 避免 classpath 缺失时退化为错误的二字切分（「的骑」「行网」「站设」）。
 */
@Slf4j
public final class LitQueryNormalizer {

    private static final Pattern WRAP_PREFIX = Pattern.compile(
        "^(基于|面向|针对|关于|浅谈|浅析|探析|试论|论|对)\\s*");
    /**
     * 去掉毕设壳子后缀，但保留「网站设计」里的「设计」：
     * 「骑行网站设计与实现」→「骑行网站设计」。
     */
    private static final Pattern WRAP_SUFFIX = Pattern.compile(
        "(的设计与实现|与实现|的研究与实现|研究与实现|的设计研究"
            + "|的研究|探析|浅析|浅谈|综述|研究进展|研究进展综述)$");
    private static final Pattern LATIN_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9.#-]*");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    private static final Set<String> STOP = Set.of(
        "基于", "面向", "针对", "关于", "浅谈", "浅析", "探析", "试论", "综述",
        "研究", "设计", "实现", "分析", "应用", "方法", "技术", "问题",
        "探索", "探讨", "发展", "对策", "现状", "及其", "以及", "与", "及", "和",
        "的", "了", "在", "中", "下", "对", "从", "以", "一种", "一个", "系统",
        "进行", "通过", "利用", "采用", "相关", "本文", "论文"
    );

    /** 领域词典：来自 jieba/lit-user.dict + 内置词，用于前向最长匹配 */
    private static final Set<String> CJK_DICT = loadCjkDict();
    private static final int CJK_DICT_MAX_LEN;

    static {
        int max = 2;
        for (String w : CJK_DICT) {
            max = Math.max(max, w.length());
        }
        CJK_DICT_MAX_LEN = max;
    }

    private static final AtomicBoolean USER_DICT_LOADED = new AtomicBoolean(false);
    private static final Object SEGMENTER_LOCK = new Object();
    private static volatile JiebaSegmenter segmenter;
    /** true 表示已尝试初始化且失败，勿反复抛错 */
    private static volatile boolean jiebaFailed;

    private LitQueryNormalizer() {
    }

    private static Set<String> loadCjkDict() {
        Set<String> dict = new HashSet<>();
        // 内置保底（不依赖资源文件）
        dict.addAll(List.of(
            "骑行", "网站", "网站设计", "网站开发", "网页设计",
            "高校", "教务", "教务管理", "教务管理系统", "管理系统",
            "微服务", "微服务架构", "软件工程", "软件架构", "软件测试", "软件质量",
            "需求分析", "数据库设计", "系统设计", "人工智能", "机器学习", "深度学习"
        ));
        try (InputStream in = LitQueryNormalizer.class.getClassLoader()
            .getResourceAsStream("jieba/lit-user.dict")) {
            if (in == null) {
                return dict;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String word = line.split("\\s+")[0];
                    if (word.length() >= 2 && CJK.matcher(word).find()) {
                        dict.add(word);
                    }
                }
            }
        } catch (Exception e) {
            // 内置词仍可用
        }
        return Set.copyOf(dict);
    }

    public static void warmup() {
        segmenter();
        JiebaSegmenter s = segmenter;
        if (s != null) {
            s.process("网站设计", JiebaSegmenter.SegMode.INDEX);
        }
    }

    private static JiebaSegmenter segmenter() {
        if (jiebaFailed) {
            return null;
        }
        JiebaSegmenter s = segmenter;
        if (s != null) {
            return s;
        }
        synchronized (SEGMENTER_LOCK) {
            if (jiebaFailed) {
                return null;
            }
            if (segmenter == null) {
                try {
                    long t0 = System.currentTimeMillis();
                    loadUserDictOnce();
                    segmenter = new JiebaSegmenter();
                    log.info("jieba segmenter ready in {} ms", System.currentTimeMillis() - t0);
                } catch (Throwable e) {
                    jiebaFailed = true;
                    segmenter = null;
                    log.warn("jieba unavailable, use domain-dict FMM only: {}", e.toString());
                }
            }
            return segmenter;
        }
    }

    private static void loadUserDictOnce() {
        if (!USER_DICT_LOADED.compareAndSet(false, true)) {
            return;
        }
        try (InputStream in = LitQueryNormalizer.class.getClassLoader()
            .getResourceAsStream("jieba/lit-user.dict")) {
            if (in == null) {
                return;
            }
            Path tmp = Files.createTempFile("lit-jieba-user-", ".dict");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            WordDictionary.getInstance().loadUserDict(tmp);
        } catch (Exception e) {
            log.warn("load jieba user dict failed: {}", e.getMessage());
        }
    }

    public static String toSearchQuery(String titleOrKeyword) {
        List<String> terms = extractTerms(titleOrKeyword);
        if (terms.isEmpty()) {
            String cleaned = stripShell(StringUtils.isBlank(titleOrKeyword) ? "" : titleOrKeyword.trim());
            return cleaned == null ? "" : cleaned;
        }
        int limit = Math.min(8, terms.size());
        return String.join(" ", terms.subList(0, limit));
    }

    public static List<String> extractTerms(String titleOrKeyword) {
        if (StringUtils.isBlank(titleOrKeyword)) {
            return List.of();
        }
        String raw = titleOrKeyword.trim();
        String cleaned = stripShell(raw);
        if (StringUtils.isBlank(cleaned)) {
            cleaned = raw;
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();

        // 1) 英文：Springboot+vue → Springboot, vue
        Matcher latin = LATIN_TOKEN.matcher(cleaned);
        while (latin.find()) {
            String tok = latin.group();
            if (tok.length() >= 2) {
                terms.add(tok);
            }
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.contains("c++")) {
            terms.add("C++");
        }
        if (cleaned.contains("C#") || lower.contains("c#")) {
            terms.add("C#");
        }

        // 2) 中文：领域词典前向最长匹配（主路径）
        String cjkPart = LATIN_TOKEN.matcher(cleaned).replaceAll(" ");
        cjkPart = cjkPart.replace('+', ' ').replaceAll("\\s+", " ").trim();
        if (StringUtils.isNotBlank(cjkPart) && CJK.matcher(cjkPart).find()) {
            for (String w : segmentCjkByDict(cjkPart)) {
                terms.add(w);
            }
            // jieba 可用时补充词典未覆盖的词（仍过滤停用词）
            JiebaSegmenter jb = segmenter();
            if (jb != null) {
                for (var token : jb.process(cjkPart, JiebaSegmenter.SegMode.INDEX)) {
                    String w = token.word == null ? "" : token.word.trim();
                    if (w.length() < 2 || STOP.contains(w) || !CJK.matcher(w).find()) {
                        continue;
                    }
                    // 只收词典已有词或长度>=3 的短语，避免「的骑」类碎片
                    if (CJK_DICT.contains(w) || w.length() >= 3) {
                        terms.add(w);
                    }
                }
            }
        }

        return new ArrayList<>(terms);
    }

    /**
     * 对连续中文段做词典前向最长匹配；跳过「的/了」等单字壳。
     */
    static List<String> segmentCjkByDict(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c < 0x4e00 || c > 0x9fff) {
                i++;
                continue;
            }
            int end = i + 1;
            while (end < n) {
                char e = text.charAt(end);
                if (e < 0x4e00 || e > 0x9fff) {
                    break;
                }
                end++;
            }
            matchForward(text, i, end, out);
            i = end;
        }
        return out;
    }

    private static void matchForward(String text, int from, int to, List<String> out) {
        int p = from;
        while (p < to) {
            String one = text.substring(p, p + 1);
            if (STOP.contains(one)) {
                p++;
                continue;
            }
            boolean matched = false;
            int maxLen = Math.min(CJK_DICT_MAX_LEN, to - p);
            for (int len = maxLen; len >= 2; len--) {
                String sub = text.substring(p, p + len);
                if (CJK_DICT.contains(sub)) {
                    if (!STOP.contains(sub)) {
                        out.add(sub);
                    }
                    p += len;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                // 未命中词典：跳过单字，避免「的骑」「行网」；二字仅在不像停用时保留
                if (p + 2 <= to) {
                    String bi = text.substring(p, p + 2);
                    if (!STOP.contains(bi) && !STOP.contains(bi.substring(0, 1))) {
                        // 仅当二字本身像专名（极少）才收录；默认跳过防噪音
                        // 这里跳过未知二字，防止「行网」「站设」
                    }
                    p += 2;
                } else {
                    p++;
                }
            }
        }
    }

    static String stripShell(String title) {
        String t = title.trim();
        Matcher pre = WRAP_PREFIX.matcher(t);
        if (pre.find()) {
            t = t.substring(pre.end());
        }
        Matcher suf = WRAP_SUFFIX.matcher(t);
        if (suf.find()) {
            t = t.substring(0, suf.start());
        }
        t = t.trim();
        if (t.startsWith("的")) {
            t = t.substring(1).trim();
        }
        if (t.endsWith("的")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }
}
