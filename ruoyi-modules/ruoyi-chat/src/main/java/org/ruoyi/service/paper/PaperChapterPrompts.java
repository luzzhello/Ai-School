package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.TocNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 论文章节级 User Prompt 注册表。
 * <p>
 * 解析策略（与可编辑大纲兼容，不依赖固定 chapterId）：
 * 1. 节点自定义 {@link TocNode#getPrompt()}；
 * 2. 特殊章节（摘要 / 参考文献 / 致谢）按 id + 标题识别；
 * 3. 标题编号（如 3.2.1）+ 一级章号 + 标题关键词；
 * 4. 通用默认 Prompt。
 */
public final class PaperChapterPrompts {

    private PaperChapterPrompts() {
    }

    /**
     * 解析章节 Prompt。
     *
     * @param session    会话
     * @param chapterId  章节 id
     * @param node       目录节点（可空）
     * @param ctx        上下文拼装器
     * @return 章节 User Prompt（不含全局上下文尾缀）
     */
    public static String resolve(PaperSession session, String chapterId, TocNode node, PromptContext ctx) {
        String title = node != null && StringUtils.isNotBlank(node.getTitle()) ? node.getTitle() : chapterId;
        List<TocNode> toc = session == null ? List.of() : session.getToc();

        if (node != null && StringUtils.isNotBlank(node.getPrompt())) {
            return promptFromCustom(node.getPrompt(), title, ctx);
        }

        // 1. 特殊章节（id 或标题）
        if (isAbstractChapter(chapterId, node)) {
            return promptAbstract(ctx);
        }
        if (isReferenceChapter(chapterId, node)) {
            return promptReferencesPage(ctx);
        }
        if (isAcknowledgementChapter(chapterId, node)) {
            return promptAcknowledgement(ctx);
        }
        if (isEnglishAbstractChapter(chapterId, node)) {
            return promptEnglishAbstract(ctx);
        }

        // 2. 标题关键词（含编号小节，如 1.2.1 / 5.1.2）
        String prompt = matchByTitle(title, ctx);
        if (prompt != null) {
            return prompt;
        }

        // 3. 按标题编号 + 一级章号 + 路径
        PaperTocPathUtils.ParsedTitle section = PaperTocPathUtils.parseTitle(title);
        int chapterMajor = PaperTocPathUtils.resolveChapterMajor(node, toc);
        String path = section == null ? PaperTocPathUtils.sectionPath(node) : section.path();
        String bareTitle = section == null ? title : section.bareTitle();

        prompt = resolveByChapterMajor(chapterMajor, path, bareTitle, title, ctx);
        if (prompt != null) {
            return prompt;
        }

        return promptDefault(title, ctx);
    }

    /**
     * 在章节 Prompt 末尾追加本节字数约束（来自大纲 {@link TocNode#getWordLimit()}）。
     */
    public static String withWordLimit(String prompt, TocNode node, Integer totalWordCount) {
        if (StringUtils.isBlank(prompt) || node == null) {
            return prompt;
        }
        Integer limit = node.getWordLimit();
        if (limit == null || limit <= 0) {
            return prompt;
        }
        StringBuilder sb = new StringBuilder(prompt);
        sb.append("\n\n----- 本节字数要求（必须遵守） -----");
        sb.append("\n请将本节正文控制在约 ").append(limit).append(" 字（允许 ±10%）");
        if (totalWordCount != null && totalWordCount > 0) {
            sb.append("，全文总字数目标为 ").append(totalWordCount).append(" 字");
        }
        sb.append("；请据此调节详略，避免明显超出或过短。");
        return sb.toString();
    }

    private static String promptFromCustom(String customPrompt, String title, PromptContext ctx) {
        return """
            撰写论文章节「%s」，请严格遵循以下写作要求：

            %s

            补充上下文：
            - 论文题目：%s
            - 系统功能：%s

            须同时遵守全篇写作要求（架构逻辑、学术严谨、反重复、正式语气、插图占位、禁止重复目录标题、禁用词：%s）。
            """.formatted(title, customPrompt.trim(), ctx.paperTitle(), ctx.tablesList(),
            PaperWritingStandards.FORBIDDEN_WORDS);
    }

    private static boolean isReferenceChapter(String chapterId, TocNode node) {
        if (StringUtils.isNotBlank(chapterId)) {
            String id = chapterId.toLowerCase();
            if ("references".equals(id) || id.contains("reference")) {
                return true;
            }
        }
        return node != null && node.getTitle() != null && node.getTitle().contains("参考文献");
    }

    private static boolean isAcknowledgementChapter(String chapterId, TocNode node) {
        if (StringUtils.isNotBlank(chapterId)) {
            String id = chapterId.toLowerCase();
            if ("acknowledgement".equals(id) || "thanks".equals(id)) {
                return true;
            }
        }
        return node != null && node.getTitle() != null && node.getTitle().contains("致谢");
    }

    private static boolean isEnglishAbstractChapter(String chapterId, TocNode node) {
        if (StringUtils.isNotBlank(chapterId)) {
            String id = chapterId.toLowerCase();
            if ("english_abstract".equals(id) || "abstract_en".equals(id)) {
                return true;
            }
        }
        if (node == null || StringUtils.isBlank(node.getTitle())) {
            return false;
        }
        String t = node.getTitle();
        return containsAny(t, "英文摘要", "ABSTRACT", "Abstract") && !isAbstractChapter(chapterId, node);
    }

    private static String resolveByChapterMajor(int chapterMajor, String path, String bareTitle,
                                              String fullTitle, PromptContext ctx) {
        return switch (chapterMajor) {
            case 1 -> resolveChapter1(path, bareTitle, fullTitle, ctx);
            case 2 -> promptChapter2Section(fullTitle, ctx);
            case 3 -> promptChapter3Section(path, bareTitle, fullTitle, ctx);
            case 4 -> promptChapter4Section(path, bareTitle, fullTitle, ctx);
            case 5 -> promptChapter5Section(fullTitle, ctx);
            case 6 -> promptChapter6Section(path, bareTitle, fullTitle, ctx);
            case 7 -> promptCh7(ctx);
            default -> null;
        };
    }

    private static String resolveChapter1(String path, String bareTitle, String fullTitle, PromptContext ctx) {
        if (matchesSection(path, "1.1") || containsInTitles(bareTitle, fullTitle, "研究背景", "背景与意义", "研究意义")) {
            return promptCh1_1(ctx);
        }
        if (matchesSection(path, "1.2.1") || containsInTitles(bareTitle, fullTitle, "国内研究", "国内现状")) {
            return promptCh1_2_1(ctx);
        }
        if (matchesSection(path, "1.2.2") || containsInTitles(bareTitle, fullTitle, "国外研究", "国外现状")) {
            return promptCh1_2_2(ctx);
        }
        if (matchesSection(path, "1.2.3")
            || (containsInTitles(bareTitle, fullTitle, "研究结论", "研究现状小结", "现状小结") && path.startsWith("1.2"))) {
            return promptCh1_2_3(ctx);
        }
        if (matchesSection(path, "1.3") || containsInTitles(bareTitle, fullTitle, "研究内容", "开发环境")) {
            return promptCh1_3(ctx);
        }
        if (matchesSection(path, "1.4") || containsInTitles(bareTitle, fullTitle, "结构安排", "论文结构")) {
            return promptCh1_4(ctx);
        }
        if (containsInTitles(bareTitle, fullTitle, "绪论")) {
            return promptDefault(fullTitle, ctx);
        }
        return null;
    }

    private static boolean matchesSection(String path, String expected) {
        return StringUtils.isNotBlank(path) && (path.equals(expected) || path.startsWith(expected + "."));
    }

    private static boolean containsInTitles(String bareTitle, String fullTitle, String... keywords) {
        return containsAny(fullTitle, keywords) || containsAny(bareTitle, keywords);
    }

    /** 判断摘要正文是否已含英文 ABSTRACT 段 */
    public static boolean hasEnglishAbstract(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String cleaned = content
            .replace("（正在生成英文 ABSTRACT，请稍候…）", "")
            .trim();
        if (!cleaned.matches("(?si).*Keywords\\s*:.*")) {
            return false;
        }
        if (cleaned.toUpperCase().contains("ABSTRACT")) {
            return true;
        }
        int kwIdx = cleaned.toLowerCase(Locale.ROOT).indexOf("keywords");
        if (kwIdx < 0) {
            return false;
        }
        String beforeKw = cleaned.substring(0, kwIdx);
        return beforeKw.chars().filter(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')).count() > 80;
    }

    // ==================== 摘要 ====================

    public static boolean isAbstractChapter(String chapterId, TocNode node) {
        if (StringUtils.isNotBlank(chapterId)) {
            String id = chapterId.toLowerCase();
            if ("abstract".equals(id) || id.contains("abstract") || id.contains("summary")) {
                return true;
            }
        }
        if (node == null || StringUtils.isBlank(node.getTitle())) {
            return false;
        }
        String title = node.getTitle();
        return title.contains("摘要")
            && !containsAny(title, "英文", "Abstract", "abstract");
    }

    /** 摘要第一阶段：仅中文摘要 + 关键词 */
    public static String promptAbstractChineseOnly(PromptContext ctx) {
        return """
            请撰写中文摘要（约300字），仅输出中文部分，不要输出英文、不要输出「摘要」标题，格式如下：

            （中文摘要正文）
            1. 包含研究目的、方法、结果和结论，重点突出结果与结论；
            2. 禁止词：%s；
            3. 结合系统实际功能与技术栈撰写。

            关键词：词1；词2；词3；词4（3－8个，用分号隔开）

            开发环境参考：%s
            论文题目：%s
            """.formatted(PaperWritingStandards.FORBIDDEN_WORDS, ctx.envInfo(), ctx.paperTitle());
    }

    /** 摘要第二阶段：根据中文摘要生成英文 ABSTRACT（含英文题目） */
    public static String promptAbstractEnglishFromChinese(String chineseAbstract, String paperTitle) {
        return """
            将下列中文摘要准确翻译为英文学术摘要，严格按以下格式输出（只输出英文部分）：

            第一行：论文题目「%s」的准确学术英文翻译（单独一行，必填，勿用中文）

            （空一行）

            （英文摘要正文 250-300 words，与中文内容对应，段落式叙述）

            （空一行）

            Keywords: word1; word2; word3

            要求：
            - 第一行必须是英文题目，不得省略或使用中文题目；
            - 不要输出 Abstract / ABSTRACT 标题行（Word 导出时会自动添加 Abstract:）；
            - Keywords 与中文关键词一一对应；使用正式学术英语；
            - 正文不要拆成多行硬换行，同一自然段保持在一段内。

            中文摘要：
            %s
            """.formatted(paperTitle, chineseAbstract);
    }

    private static String promptAbstract(PromptContext ctx) {
        return """
            请撰写论文「摘要」章节，必须同时包含中文摘要与英文摘要（英文为中文的学术翻译），严格按以下格式输出（不要重复输出章节标题「摘要」，直接从正文开始）：

            （中文摘要正文，约300字）
            1. 包含研究目的、方法、结果和结论，重点突出结果与结论；
            2. 语言精炼、学术规范，禁止：%s；
            3. 结合系统实际功能与技术栈，结构参考：
               随着[行业/领域]发展 → 现有方式不足 → 设计/开发了[系统名] → 采用[技术栈] → 实现[核心功能] → 测试验证 → 应用价值。

            关键词：词1；词2；词3；词4（3－8个，用分号隔开）

            ABSTRACT

            （英文摘要，约250-300 words，与中文摘要内容对应，为准确学术翻译而非另写）
            使用正式学术英语，同样涵盖 purpose, methods, results, conclusions。

            Keywords: word1; word2; word3; word4

            注意：
            - 英文关键词与中文关键词一一对应（3－8个）；
            - 两段摘要之间空一行；
            - 不要省略 ABSTRACT 与 Keywords 标题行。
            开发环境参考：%s
            论文题目：%s
            """.formatted(PaperWritingStandards.FORBIDDEN_WORDS, ctx.envInfo(), ctx.paperTitle());
    }

    private static String promptEnglishAbstract(PromptContext ctx) {
        return """
            撰写英文摘要 ABSTRACT（约250-300 words），要求：
            1. 与论文中文摘要内容对应，为准确学术翻译；
            2. 包含 research purpose, methods, results, conclusions；
            3. 文末单独一行：Keywords: word1; word2; word3（3-5个，与中文关键词对应）；
            4. 使用正式学术英语。
            论文题目：%s
            """.formatted(ctx.paperTitle());
    }

    // ==================== 第一章 绪论 ====================

    private static String promptCh1_1(PromptContext ctx) {
        return """
            撰写「1.1 研究背景与意义」，要求：
            1. 说明论文主题、范围和目的，阐述预期结果与研究意义；
            2. 字数控制在500字以内；
            3. 至少引用3篇参考文献；每处引用须在所引内容最末句句末标注角标，格式为[1][2][3]（阿拉伯数字+方括号），只概述内容勿罗列文献题名；
            4. 结构：行业/领域背景 → 传统方式局限 → 技术发展机遇 → 本研究的必要性与意义。
            参考文献：%s
            """.formatted(ctx.refsBackground());
    }

    private static String promptCh1_2_1(PromptContext ctx) {
        return """
            撰写「1.2.1 国内研究现状」，要求：
            1. 字数500-550字；
            2. 引用中文文献，叙述可采用「XXX学者（年份）提出/设计了…，带来了…便利，但仍存在…问题[n]」；
            3. 每一处引用必须在句末加角标[n]（与文献序号一致），禁止只写学者年份而不加[n]；勿罗列文献题名；
            4. 文献内容简要描述即可，体现研究脉络与不足。
            中文参考文献：%s
            """.formatted(ctx.refsChinese());
    }

    private static String promptCh1_2_2(PromptContext ctx) {
        return """
            撰写「1.2.2 国外研究现状」，要求：
            1. 字数500-550字；
            2. 引用英文文献，概述国外研究特点（技术整合、功能扩展、服务生态等）；
            3. 每一处引用必须在所引内容最末句句末加角标[n]（阿拉伯数字+方括号），序号与英文文献列表一致；勿罗列文献题名；
            4. 指出国外方案在本土化方面的局限，为本研究留出空间。
            英文参考文献：%s
            """.formatted(ctx.refsEnglish());
    }

    private static String promptCh1_2_3(PromptContext ctx) {
        return """
            撰写「1.2.3 研究结论（研究现状小结）」，要求：
            1. 字数350-400字；
            2. 综述国内外研究现状，归纳现有研究的不足与差距；综述处须保留或补充角标[n]；
            3. 自然引出本研究的必要性和创新点，为后文铺垫；
            4. 不要以「综上所述」开头或结尾。
            可引用参考文献：%s
            """.formatted(ctx.allRefs());
    }

    private static String promptCh1_3(PromptContext ctx) {
        return """
            撰写「1.3 研究内容/开发环境」，要求：
            1. 字数350字以内；
            2. 说明本系统通过设计与实现完成了哪些核心功能（结合数据库表/模块推断）；
            3. 列出开发环境：操作系统、开发工具、编程语言、前端/后端框架、数据库等。
            开发环境参考：%s
            系统功能模块：%s
            """.formatted(ctx.envInfo(), ctx.tablesList());
    }

    private static String promptCh1_4(PromptContext ctx) {
        return """
            撰写「1.4 论文结构安排」，要求：
            1. 逐章介绍论文各章内容，每章100-200字；
            2. 涵盖：第一章绪论、第二章关键技术、第三章需求分析、第四章系统设计、
               第五章系统实现、第六章系统测试、第七章总结与展望；
            3. 说明各章之间的逻辑关系，体现论文整体结构。
            """;
    }

    // ==================== 第二章 关键技术 ====================

    private static String promptChapter2Section(String title, PromptContext ctx) {
        return """
            撰写「%s」（第二章 关键技术介绍），要求：
            1. 针对该技术的特点、优势及在本系统中的具体应用进行介绍；
            2. 字数约200-300字，学术语气，结合项目实际技术栈；
            3. 说明选择该技术的原因及其在本系统中的角色；
            4. 介绍技术来源或既有研究时，在句末标注参考文献角标[n]（至少1处，序号须与列表一致）；
            5. 不要输出本节标题或 Markdown 标题（如 ## 2.1 xxx），直接从正文第一段开始。
            开发环境与技术栈：%s
            可引用参考文献：%s
            """.formatted(title, ctx.envInfo(), ctx.allRefs());
    }

    // ==================== 第三章 需求分析 ====================

    private static String promptChapter3Section(String path, String bareTitle, String fullTitle, PromptContext ctx) {
        if (containsInTitles(bareTitle, fullTitle, "调研", "用户调研", "需求调研")) {
            return """
                撰写「%s」，要求：
                1. 包含项目背景和目标、目标用户群体分析；
                2. 说明调研方法（问卷调查、用户访谈等）及主要发现；
                3. 结合系统实际面向的用户角色（如普通用户、管理员）展开；
                4. 字数400-500字，段落式叙述。
                """.formatted(fullTitle);
        }
        if (containsInTitles(bareTitle, fullTitle, "功能需求", "功能模块", "功能分析")) {
            String userRole = PaperBusinessModuleResolver.resolveUserRoleLabel(ctx.paperTitle());
            return """
                撰写「%s」，要求：
                1. 先写 80 字以内的总体功能需求概述；
                2. 分「%s」和「管理员」两部分撰写（角色名固定使用上述称呼）；
                3. 每部分结构：一段 200-300 字的功能需求描述（段落式，分点说明核心功能）+ 换行 + 【此处插入%s用例图】或【此处插入管理员用例图】；
                4. 功能需求须与数据库表、系统模块一致，禁止空泛描述；
                5. 不要输出 Markdown 标题，正文用自然段即可。
                系统数据表/模块：%s
                """.formatted(fullTitle, userRole, userRole, ctx.tablesList());
        }
        if (containsInTitles(bareTitle, fullTitle, "非功能")) {
            return """
                撰写「%s」，要求：
                1. 从性能、安全、易用性、可扩展性、可维护性等方面分析；
                2. 字数300字以内，分点论述；
                3. 结合本系统实际特点，给出具体指标（如响应时间、并发支持等）。
                """.formatted(fullTitle);
        }
        if (containsInTitles(bareTitle, fullTitle, "用例")) {
            return """
                撰写「%s」，要求：
                1. 按用户角色（如普通用户、管理员）分别介绍用例；
                2. 每个角色用段落方式描述主要用例，每个用例约200字以内；
                3. 需插入占位：【此处插入XX角色用例图】；
                4. 结合系统实际功能模块撰写。
                功能模块：%s
                """.formatted(fullTitle, ctx.tablesList());
        }
        if (containsInTitles(bareTitle, fullTitle, "技术可行")) {
            return promptFeasibility(fullTitle, "技术", ctx);
        }
        if (containsInTitles(bareTitle, fullTitle, "经济可行")) {
            return promptFeasibility(fullTitle, "经济", ctx);
        }
        if (containsInTitles(bareTitle, fullTitle, "操作可行")) {
            return promptFeasibility(fullTitle, "操作", ctx);
        }
        if (containsInTitles(bareTitle, fullTitle, "可行")) {
            return promptFeasibility(fullTitle, "综合", ctx);
        }
        if (containsInTitles(bareTitle, fullTitle, "性能")) {
            return """
                撰写「%s」，要求：分析系统性能需求（响应时间、并发量、数据处理能力等），字数200-300字。
                """.formatted(fullTitle);
        }
        if (containsInTitles(bareTitle, fullTitle, "流程", "操作流")) {
            return """
                撰写「%s」，要求：描述系统主要操作流程，可配合流程图占位【此处插入XX流程图】，字数300-400字。
                """.formatted(fullTitle);
        }
        if (StringUtils.isNotBlank(path) && path.startsWith("3")) {
            return promptDefault(fullTitle, ctx);
        }
        return null;
    }

    private static String promptFeasibility(String title, String type, PromptContext ctx) {
        return """
            撰写「%s」，要求：
            1. 从%s角度分析本系统开发/运行的可行性；
            2. 字数200字以内，结合项目使用的技术栈和实际条件；
            3. 给出明确结论：可行/基本可行，并简要说明理由。
            技术环境：%s
            """.formatted(title, type, ctx.envInfo());
    }

    // ==================== 第四章 系统设计 ====================

    private static String promptChapter4Section(String path, String bareTitle, String fullTitle, PromptContext ctx) {
        if (containsInTitles(bareTitle, fullTitle, "架构设计", "系统架构") && !containsInTitles(bareTitle, fullTitle, "结构")) {
            return """
                撰写「%s」，要求：
                1. 描述系统整体架构（如B/S架构、前后端分离、三层架构等），字数350字以内；
                2. 说明各层职责与技术选型；
                3. 插入占位：【此处插入系统架构图】；
                4. 结合开发环境：%s
                """.formatted(fullTitle, ctx.envInfo());
        }
        if (containsInTitles(bareTitle, fullTitle, "体系结构", "功能结构", "模块设计", "功能模块设计")) {
            return """
                撰写「%s」，要求：
                1. 按用户角色（如普通用户端、管理端）分节描述功能结构；
                2. 说明这样设计的作用与优势，每节约200字；
                3. 插入占位：【此处插入系统功能结构图】；
                4. 功能模块参考：%s
                """.formatted(fullTitle, ctx.tablesList());
        }
        if (containsInTitles(bareTitle, fullTitle, "流程设计", "系统流程")) {
            if (isFlowSubsectionPath(path)) {
                return promptCh4FlowSubsection(fullTitle, bareTitle, ctx);
            }
            if (isFlowOverviewPath(path, fullTitle)) {
                return promptCh4FlowOverview(fullTitle, ctx);
            }
            return promptCh4FlowOverview(fullTitle, ctx);
        }
        if (isFlowSubsectionPath(path) && !containsInTitles(bareTitle, fullTitle, "数据库", "E-R", "ER", "表设计", "架构", "功能结构")) {
            return promptCh4FlowSubsection(fullTitle, bareTitle, ctx);
        }
        if (containsInTitles(bareTitle, fullTitle, "数据库设计") && !containsInTitles(bareTitle, fullTitle, "结构", "表设计")) {
            return """
                撰写「%s」总体说明，要求：
                1. 描述数据库设计的作用与整体设计思路，300字以内；
                2. 概述主要数据表及其关系。
                """.formatted(fullTitle);
        }
        if (containsInTitles(bareTitle, fullTitle, "数据库结构设计", "数据库结构")
            || containsInTitles(bareTitle, fullTitle, "E-R", "ER", "概念模型")) {
            return promptCh4_4_1(fullTitle, ctx);
        }
        if (containsInTitles(bareTitle, fullTitle, "表设计", "三线表")
            && !containsInTitles(bareTitle, fullTitle, "结构", "E-R", "ER")) {
            return promptCh4_4_2(fullTitle, ctx);
        }
        if (StringUtils.isNotBlank(path) && path.startsWith("4")) {
            return promptDefault(fullTitle, ctx);
        }
        return null;
    }

    private static String promptCh4FlowOverview(String title, PromptContext ctx) {
        return """
            撰写「%s」概述，要求：
            1. 说明系统主要业务流程设计的总体思路，150-250字；
            2. 概括各核心功能模块的操作流程及其相互关系；
            3. 段落叙述，不使用编号列表；
            4. 不插入流程图占位，具体流程图在各流程子节（如 4.3.1）中单独呈现。
            系统功能参考：%s
            """.formatted(title, ctx.tablesList());
    }

    private static String promptCh4FlowSubsection(String fullTitle, String bareTitle, PromptContext ctx) {
        String moduleName = StringUtils.isNotBlank(bareTitle) ? bareTitle.trim() : extractModuleName(fullTitle);
        return """
            撰写「%s」（系统流程设计），要求：
            1. 以段落形式描述「%s」的业务流程，200-300字；
            2. 说明用户操作步骤、系统校验逻辑与结果反馈，语言精炼、学术规范；
            3. 禁止使用编号列表、分点罗列或小标题；
            4. 段落后另起一行插入占位：【此处插入%s流程图】；
            5. 与论文题目「%s」及数据库表 %s 保持一致，禁止空泛套话。

            流程图说明（占位将由系统自动生成）：标准功能流程图，含开始/结束、处理步骤、条件判断分支。
            """.formatted(fullTitle, moduleName, moduleName, ctx.paperTitle(), ctx.tablesList());
    }

    private static String promptCh4_4_1(String fullTitle, PromptContext ctx) {
        return """
            撰写「%s」，要求：
            1. 先用150-200字段落说明数据库概念结构（陈氏 E-R 表示法）的设计思路；
            2. 再用200-250字段落专门描述「系统总体 E-R 图」：逐一说明图中出现的实体、菱形联系及其 1/n 基数含义，只写存在外键关联的实体，不写无关联的孤立表；
            3. 禁止使用 PlantUML、Mermaid、代码块或 ASCII 图输出 ER 图；
            4. 上述文字段落后必须另起一行插入占位（括号与文字须完全一致，禁止改写）：
               【此处插入总体E-R图】
            5. 随后仅对下列「在总体 E-R 图中存在关联」的业务实体，各写80-120字属性说明，每段后插入对应占位（实体名须完全一致；不要为用户/管理员等等角色实体写属性图）：
            %s
            6. 段落叙述，禁止编号列表；与论文题目「%s」保持一致。
            主要表间关联：%s
            表结构：%s
            """.formatted(
            fullTitle,
            buildErEntityPlaceholderGuide(ctx.erEntityLabels()),
            ctx.paperTitle(),
            ctx.erRelationSummary(),
            ctx.sqlParsedText());
    }

    private static String buildErEntityPlaceholderGuide(String erEntityLabels) {
        if (StringUtils.isBlank(erEntityLabels)) {
            return "   - 各业务实体：说明后插入【此处插入XX实体属性图】";
        }
        StringBuilder sb = new StringBuilder();
        for (String entity : erEntityLabels.split("[、,，;；]")) {
            String name = entity.trim();
            if (name.length() < 2) {
                continue;
            }
            sb.append("\n   - ").append(name).append("：说明后插入【此处插入")
                .append(name).append("实体属性图】");
        }
        return sb.isEmpty()
            ? "   - 各业务实体：说明后插入【此处插入XX实体属性图】"
            : sb.toString();
    }

    /**
     * 从外键关联推断需绘制实体属性图的业务实体（不含用户/管理员等角色实体）。
     */
    static String inferErEntityLabels(PaperSession.SqlParsed sqlParsed) {
        if (sqlParsed == null) {
            return "";
        }
        Set<String> relatedTables = collectRelatedTables(sqlParsed);
        if (relatedTables.isEmpty()) {
            return "";
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> labels = new ArrayList<>();
        for (String table : relatedTables) {
            String label = PaperTableLabelResolver.resolveEntityLabel(table, sqlParsed);
            if (StringUtils.isBlank(label) || isErRoleEntity(label)) {
                continue;
            }
            if (!seen.add(normalizeErKey(label))) {
                continue;
            }
            labels.add(label);
            if (labels.size() >= 5) {
                break;
            }
        }
        return String.join("、", labels);
    }

    static String inferErRelationSummary(PaperSession.SqlParsed sqlParsed) {
        if (sqlParsed == null || sqlParsed.getRelations() == null || sqlParsed.getRelations().isEmpty()) {
            return "（将根据优化后的外键关系自动绘制总体 E-R 图）";
        }
        List<String> parts = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        for (org.ruoyi.domain.paper.Relation relation : sqlParsed.getRelations()) {
            if (isInfrastructureErTable(relation.getTable1()) || isInfrastructureErTable(relation.getTable2())) {
                continue;
            }
            String a = PaperTableLabelResolver.resolveEntityLabel(relation.getTable1(), sqlParsed);
            String b = PaperTableLabelResolver.resolveEntityLabel(relation.getTable2(), sqlParsed);
            if (StringUtils.isBlank(a) || StringUtils.isBlank(b)) {
                continue;
            }
            String key = normalizeErKey(a) + "->" + normalizeErKey(b);
            if (!dedupe.add(key)) {
                continue;
            }
            String type = StringUtils.isNotBlank(relation.getType()) ? relation.getType() : "1:N";
            parts.add(a + "与" + b + "(" + type + ")");
            if (parts.size() >= 8) {
                break;
            }
        }
        return parts.isEmpty()
            ? "（将根据优化后的外键关系自动绘制总体 E-R 图）"
            : String.join("；", parts);
    }

    private static Set<String> collectRelatedTables(PaperSession.SqlParsed sqlParsed) {
        Set<String> tables = new LinkedHashSet<>();
        if (sqlParsed.getRelations() == null) {
            return tables;
        }
        for (org.ruoyi.domain.paper.Relation relation : sqlParsed.getRelations()) {
            if (StringUtils.isNotBlank(relation.getTable1()) && !isInfrastructureErTable(relation.getTable1())) {
                tables.add(relation.getTable1());
            }
            if (StringUtils.isNotBlank(relation.getTable2()) && !isInfrastructureErTable(relation.getTable2())) {
                tables.add(relation.getTable2());
            }
        }
        return tables;
    }

    private static boolean isErRoleEntity(String label) {
        if (StringUtils.isBlank(label)) {
            return false;
        }
        return label.contains("用户") || label.contains("管理员") || label.contains("会员");
    }

    private static boolean isInfrastructureErTable(String table) {
        if (StringUtils.isBlank(table)) {
            return true;
        }
        String lower = table.toLowerCase(Locale.ROOT);
        return lower.startsWith("sys_") || lower.startsWith("qrtz_") || lower.startsWith("act_")
            || lower.startsWith("gen_") || lower.contains("dict") || lower.contains("config")
            || lower.contains("log") || lower.contains("menu") || lower.contains("role")
            || lower.contains("permission");
    }

    private static String normalizeErKey(String label) {
        return label.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String promptCh4_4_2(String fullTitle, PromptContext ctx) {
        return """
            撰写「%s」，要求：
            1. 首段150-200字：说明数据库表设计原则（合理存储、外键关联、索引与约束、便于维护等），学术语气，段落叙述；
            2. 第二段80-120字：概括本系统约5～10张核心业务数据表及各自职责（不列字段明细）；
            3. 随后逐表输出（仅下列核心表，共5～10张，禁止字典/日志/中间关联表），每张表固定两段：
               a) 80-120字说明该表用途，句末写「如表 4-Y 所示」（Y 从1递增）；
               b) 下一段独占一行插入占位（表名须完全一致）：
            %s
            4. 禁止自行输出 Markdown/HTML/ASCII 表格（禁止「字段名|类型|说明」等形式），表结构由系统按「字段名称、类型、长度、允许空值(Y/N)、主键(Y/N)、备注」六列自动插入；
            5. 禁止编号列表，与论文题目「%s」保持一致；
            6. 正文叙述必须使用中文表名（如「用户信息表」），禁止出现 sys_xxx、tb_xxx 等 SQL 物理表名。
            字段结构参考：%s
            """.formatted(
            fullTitle,
            buildDbTablePlaceholderGuide(ctx.dbTableLabels()),
            ctx.paperTitle(),
            ctx.columnsText());
    }

    /** 4.4.2 需展示三线表的核心业务表（中文表名，约 5～10 张） */
    static String inferDbTableLabels(PaperSession.SqlParsed sqlParsed) {
        List<String> tables = PaperDbTableSelector.selectKeyBusinessTables(sqlParsed);
        if (tables.isEmpty()) {
            return "";
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> labels = new ArrayList<>();
        for (String table : tables) {
            String label = PaperTableLabelResolver.resolveTableLabel(table, sqlParsed);
            if (StringUtils.isBlank(label)) {
                continue;
            }
            if (!seen.add(normalizeErKey(label))) {
                continue;
            }
            labels.add(label);
        }
        return String.join("、", labels);
    }

    private static String buildDbTablePlaceholderGuide(String dbTableLabels) {
        if (StringUtils.isBlank(dbTableLabels)) {
            return "   【此处插入XX表结构】";
        }
        StringBuilder sb = new StringBuilder();
        for (String label : dbTableLabels.split("[、,，;；]")) {
            String name = label.trim().replaceAll("表$", "");
            if (name.length() < 1) {
                continue;
            }
            sb.append("\n   - ").append(label.trim()).append("：说明后插入【此处插入")
                .append(name).append("表结构】");
        }
        return sb.isEmpty() ? "   【此处插入XX表结构】" : sb.toString();
    }

    // ==================== 第五章 系统实现 ====================

    private static String promptChapter5Section(String title, PromptContext ctx) {
        String moduleName = extractModuleName(title);
        return """
            撰写「%s」（第五章 系统实现），要求：
            1. 结合论文题目与已上传的项目代码，撰写该功能模块的系统实现描述，说明模块在系统中的实际作用与实现方式；
            2. 必须使用段落方式叙述，连贯成文，不要使用编号列表、分点罗列或小标题；
            3. 全节字数控制在200字以内，语言精炼、学术规范；禁止词：%s；
            4. 须从代码中提炼真实的 Controller/Service/Mapper 调用链、核心方法或接口逻辑，结合数据库表说明数据交互，禁止空泛套话；
            5. 仅写实现层面内容，不得重复第三章需求分析或第四章设计中的功能描述与流程说明；
            6. 描述须与论文题目及本模块功能一致；涉及数据表时使用中文表名，禁止写 sys_xxx 等物理表名；
            7. 段落后可另起一行插入占位：【此处插入%s功能界面截图】，并说明截图应展示的主要界面元素。

            论文题目：%s
            代码参考：
            %s
            """.formatted(title, PaperWritingStandards.FORBIDDEN_WORDS, moduleName, ctx.paperTitle(), ctx.codeSnippet());
    }

    // ==================== 第六章 系统测试 ====================

    private static String promptCh6_1(PromptContext ctx) {
        return """
            撰写「6.1 测试目的」，要求：
            1. 说明系统测试的目标、范围与意义；
            2. 字数350-450字，学术语气；
            3. 涵盖功能验证、性能验证、用户体验验证等方面。
            """;
    }

    private static String promptCh6_2(PromptContext ctx) {
        return """
            撰写「6.2 测试环境与工具」，要求：
            1. 使用Markdown表格展示测试环境，列：类别|名称|版本/说明；
            2. 至少包含：操作系统、浏览器、开发/测试工具、数据库、后端框架等；
            3. 表格后附1-2句说明。
            开发环境：%s
            """.formatted(ctx.envInfo());
    }

    private static String promptCh6_3(PromptContext ctx) {
        return """
            撰写「6.3 测试过程」，要求：
            1. 按功能模块分别设计测试用例表，每个模块一张表，每张表至少8条用例；
            2. 表格列：用例编号|用例名称|测试功能|输入数据|预期输出|测试结果；
            3. 每个表前用1-2句话说明测试了哪些功能；
            4. 测试功能必须来自系统实际模块（非虚构）；
            5. 表标题格式：表6-X XXX功能测试用例。
            可测功能模块：%s
            """.formatted(ctx.tablesList());
    }

    private static String promptCh6_4(PromptContext ctx) {
        return """
            撰写「6.4 系统测试结论」，要求：
            1. 总结测试结果，说明系统是否达到预期目标；
            2. 字数350-450字；
            3. 指出测试中发现的问题（如有）及整体质量评价。
            """;
    }

    private static String promptChapter6Section(String path, String bareTitle, String fullTitle, PromptContext ctx) {
        if (matchesSection(path, "6.1") || containsInTitles(bareTitle, fullTitle, "目的")) {
            return promptCh6_1(ctx);
        }
        if (matchesSection(path, "6.2") || containsInTitles(bareTitle, fullTitle, "环境", "工具")) {
            return promptCh6_2(ctx);
        }
        if (matchesSection(path, "6.3") || containsInTitles(bareTitle, fullTitle, "过程", "用例")) {
            return promptCh6_3(ctx);
        }
        if (matchesSection(path, "6.4") || containsInTitles(bareTitle, fullTitle, "结论", "结果")) {
            return promptCh6_4(ctx);
        }
        if (containsInTitles(bareTitle, fullTitle, "本章小结", "小结")) {
            return """
                撰写「%s」，要求：
                1. 概括本章测试工作要点与结论，150-250字；
                2. 与全章测试内容呼应，不要引入新测试项。
                """.formatted(fullTitle);
        }
        if (StringUtils.isNotBlank(path) && path.startsWith("6")) {
            return promptDefault(fullTitle, ctx);
        }
        return null;
    }

    // ==================== 第七章 / 致谢 / 参考文献 ====================

    private static String promptCh7(PromptContext ctx) {
        return """
            撰写「第七章 总结与展望」，要求：
            1. 包含两部分：（一）研究结论总结 — 归纳主要成果、学术价值与实践意义；
               （二）研究不足与展望 — 分析不足并提出未来改进方向；
            2. 结论应明确、简练、完整、准确；
            3. 总字数800-1200字，不要使用「综上所述」等词。
            """;
    }

    private static String promptAcknowledgement(PromptContext ctx) {
        return """
            撰写「致谢」，要求：
            1. 以即将毕业的大学生视角，感谢导师、同学、学校、家人；
            2. 字数450字以内，情感真挚，语言朴实；
            3. 保留占位符：[导师姓名]、[学校名称]。
            """;
    }

    private static String promptReferencesPage(PromptContext ctx) {
        return """
            生成「参考文献」章节，要求：
            1. 按 GB/T 7714 格式列出全部参考文献，带序号[1][2]...；
            2. 在章节开头加提示：请在提交前通过知网核实文献真实性；
            3. 直接使用以下文献列表输出，不要编造新文献。
            文献列表：%s
            """.formatted(ctx.allRefs());
    }

    // ==================== 标题关键词匹配 ====================

    private static String matchByTitle(String title, PromptContext ctx) {
        if (containsAny(title, "摘要") && containsAny(title, "abstract", "Abstract", "英文")) {
            return promptEnglishAbstract(ctx);
        }
        if (title.contains("摘要")) return promptAbstract(ctx);
        if (containsAny(title, "1.1", "研究背景")) return promptCh1_1(ctx);
        if (containsAny(title, "1.2.1", "国内研究", "国内现状")) return promptCh1_2_1(ctx);
        if (containsAny(title, "1.2.2", "国外研究", "国外现状")) return promptCh1_2_2(ctx);
        if (containsAny(title, "1.2.3", "研究结论") && title.contains("1.2")) return promptCh1_2_3(ctx);
        if (containsAny(title, "1.3", "研究内容", "开发环境")) return promptCh1_3(ctx);
        if (containsAny(title, "1.4", "结构安排", "论文结构")) return promptCh1_4(ctx);
        if (containsAny(title, "致谢")) return promptAcknowledgement(ctx);
        if (containsAny(title, "参考文献")) return promptReferencesPage(ctx);
        if (containsAny(title, "总结", "展望") && containsAny(title, "七", "7", "第7", "7 ")) return promptCh7(ctx);
        if (matchesChapter(title, 2) || title.matches("(?i).*2\\.\\d.*")) {
            if (containsAny(title, "技术", "框架", "数据库", "简介", "介绍")) {
                return promptChapter2Section(title, ctx);
            }
        }
        if (matchesChapter(title, 5) || title.matches("(?i).*5\\.\\d.*")) {
            if (containsAny(title, "实现", "模块", "功能", "小结")) {
                if (containsAny(title, "本章小结", "小结") && !containsAny(title, "功能")) {
                    return """
                        撰写「%s」，要求：概括本章系统实现要点，150-250字，与各模块实现内容呼应。
                        """.formatted(title);
                }
                return promptChapter5Section(title, ctx);
            }
        }
        if (title.matches("(?i).*4\\.\\d+\\.\\d+.*")
            && !containsAny(title, "数据库", "E-R", "ER图", "表设计", "架构设计", "功能结构")) {
            String bare = title.replaceAll("^[\\d.\\s、]+", "").trim();
            return promptCh4FlowSubsection(title, bare, ctx);
        }
        return null;
    }

    /** 匹配一级章节标题，如「2 相关技术」「二、相关技术」 */
    private static boolean matchesChapter(String title, int chapterNo) {
        if (title == null) {
            return false;
        }
        String num = String.valueOf(chapterNo);
        return title.startsWith(num + " ")
            || title.startsWith(num + "、")
            || title.contains("第" + num + "章");
    }

    private static String promptDefault(String title, PromptContext ctx) {
        return """
            撰写论文章节「%s」，要求：
            1. 符合计算机类毕业论文学术规范，结合系统实际功能与数据库内容，本节在整篇架构中定位明确；
            2. 正式学术语气，段内层次清晰，论述严谨具体；禁止词：%s；
            3. 与前后章节保持逻辑连贯，不得重复其他章已展开的设计、流程或实现细节；
            4. 需要插图处使用占位：【此处插入XXX图】，并说明该图应展示的内容与作用；
            5. 描述数据库与业务时使用中文表名，禁止直接写 SQL 物理表名（如 sys_user、tb_order）；
            6. 禁止输出本节章节标题、编号或 Markdown 标题，正文直接从第一段自然段开始。
            系统功能：%s
            """.formatted(title, PaperWritingStandards.FORBIDDEN_WORDS, ctx.tablesList());
    }

    // ==================== 工具方法 ====================

    private static boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static boolean isFlowSubsectionPath(String path) {
        return StringUtils.isNotBlank(path) && path.matches("4\\.\\d+\\.\\d+");
    }

    private static boolean isFlowOverviewPath(String path, String fullTitle) {
        return StringUtils.isNotBlank(path)
            && path.matches("4\\.\\d+")
            && containsInTitles(null, fullTitle, "流程设计", "系统流程");
    }

    private static String extractModuleName(String title) {
        if (title == null) return "该";
        return title.replaceAll("^[\\d.\\s、]+", "").replace("模块实现", "").replace("实现", "").trim();
    }

    /**
     * Prompt 上下文（由 Service 填充数据）。
     */
    public record PromptContext(
        String paperTitle,
        String envInfo,
        String codeSnippet,
        String sqlParsedText,
        String columnsText,
        String tablesList,
        String erEntityLabels,
        String erRelationSummary,
        String dbTableLabels,
        String refsBackground,
        String refsChinese,
        String refsEnglish,
        String allRefs
    ) {}
}
