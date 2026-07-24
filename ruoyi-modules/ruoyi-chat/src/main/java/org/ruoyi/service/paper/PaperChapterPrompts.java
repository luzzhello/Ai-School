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

    public static boolean isAcknowledgementChapter(String chapterId, TocNode node) {
        if (StringUtils.isNotBlank(chapterId)) {
            String id = chapterId.toLowerCase();
            if ("acknowledgement".equals(id) || "thanks".equals(id) || id.contains("acknowledg")) {
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
            结构必须按下列顺序压缩表达（可同段连贯书写，勿分条列标题）：
            1. 问题：业务场景与待解决痛点（一两句）；
            2. 方法：系统定位与关键技术栈；
            3. 结果：实现的核心能力/模块（写具体，勿空喊「功能完善」）；
            4. 价值：应用意义或验证结论（一句收束）。
            要求：重点在结果与结论；禁止词：%s；结合系统实际功能与技术栈；不要大段行业背景铺垫。

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
            结构顺序：问题（场景与痛点）→ 方法（系统与技术栈）→ 结果（核心功能/能力，写具体）→ 价值（结论或应用意义）。
            要求：重点突出结果与结论；语言精炼；禁止：%s；不要冗长行业背景；结合系统实际功能与技术栈。

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
            1. 结构：领域/业务背景 → 现有方式或系统的具体不足（缺口）→ 技术条件使改进成为可能 → 本研究/本系统的目标、范围与意义；
            2. 缺口必须落到可被本系统解决的具体问题，禁止只写「信息化水平有待提高」类空话；
            3. 字数控制在500字以内；
            4. 至少引用3篇参考文献；每处引用须在所引内容最末句句末标注角标[n]，只概述内容勿罗列文献题名；
            5. 段落按「主张→文献/事实依据→引出本系统必要性」组织。
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
            2. 综述国内外现状后，明确归纳「已有工作能做什么 / 仍缺什么」；缺口须能被后续本系统功能承接；
            3. 自然引出本研究目标与边界（做什么、不做什么），为后文铺垫；综述处须保留或补充角标[n]；
            4. 不要以「综上所述」开头或结尾，不要空泛喊「具有重要意义」。
            可引用参考文献：%s
            """.formatted(ctx.allRefs());
    }

    private static String promptCh1_3(PromptContext ctx) {
        return """
            撰写「1.3 研究内容/开发环境」，要求：
            1. 字数350字以内；
            2. 先用条目化叙述说明本系统完成的核心功能（须与模块/表一致，写具体能力而非口号）；
            3. 再列出开发环境：操作系统、开发工具、编程语言、前端/后端框架、数据库等，名称写法与全文技术栈保持一致；
            4. 明确研究范围边界：以本系统已实现模块为准，不夸大未实现能力。
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
            1. 每段按「是什么 → 为何选用 → 在本系统中的角色/落点」组织，禁止百科式堆砌概念；
            2. 字数约200-300字，学术语气，技术名称与项目技术栈写法保持一致；
            3. 「为何选用」须给出与本系统相关的具体理由（至少一句），勿只写「该技术应用广泛」；
            4. 介绍技术来源或既有研究时，在句末标注参考文献角标[n]（至少1处，序号须与列表一致）；无依据不编造引用；
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
                1. 结构：业务场景与目标用户 → 调研方法与样本/来源 → 主要发现（痛点）→ 对后续功能需求的约束；
                2. 发现须落到可被本系统模块承接的具体问题，禁止只写「用户体验有待提升」；
                3. 结合系统实际角色（如普通用户、管理员）展开；段落按「主张→调研/业务依据→边界」组织；
                4. 字数400-500字，段落式叙述；勿展开成设计或实现细节。
                """.formatted(fullTitle);
        }
        if (containsInTitles(bareTitle, fullTitle, "功能需求", "功能模块", "功能分析")) {
            String userRole = PaperBusinessModuleResolver.resolveUserRoleLabel(ctx.paperTitle());
            return """
                撰写「%s」，要求：
                1. 结构：总体功能主张（80字内）→ 分角色能力与约束 → 用例图占位；
                2. 分「%s」和「管理员」两部分（角色名固定使用上述称呼）；
                3. 每部分：一段 200-300 字功能需求（须写清能做什么、关键输入/输出或业务规则）+ 换行 + 【此处插入%s用例图】或【此处插入管理员用例图】；
                4. 功能须与数据库表、系统模块一致；证据落到具体模块/表中文名，禁止空泛描述；
                5. 只写「需求是什么」，禁止写成架构设计或代码实现；不要输出 Markdown 标题。
                系统数据表/模块：%s
                """.formatted(fullTitle, userRole, userRole, ctx.tablesList());
        }
        if (containsInTitles(bareTitle, fullTitle, "非功能")) {
            return """
                撰写「%s」，要求：
                1. 结构：性能 → 安全 → 易用性 → 可扩展/可维护（可按重要性取舍，不必面面俱到）；
                2. 每项给出可核对的指标或约束（如响应时间、权限边界、浏览器兼容），勿只列形容词；
                3. 字数300字以内；结合本系统实际，说明指标适用范围（边界）。
                """.formatted(fullTitle);
        }
        if (containsInTitles(bareTitle, fullTitle, "用例")) {
            return """
                撰写「%s」，要求：
                1. 结构：按角色划分 → 各角色核心用例（参与者、目标、主成功场景要点）→ 用例图占位；
                2. 每个角色用段落描述主要用例，每个用例约200字以内；证据对应真实功能模块；
                3. 需插入占位：【此处插入XX角色用例图】；禁止写成类图/时序实现细节。
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
                撰写「%s」，要求：
                1. 结构：性能目标主张 → 具体指标（响应时间、并发、数据处理量等）→ 适用场景边界；
                2. 字数200-300字；指标须可被后续测试章核对，勿空喊「高性能」。
                """.formatted(fullTitle);
        }
        if (containsInTitles(bareTitle, fullTitle, "流程", "操作流")) {
            return """
                撰写「%s」，要求：
                1. 结构：主流程目标 → 关键步骤与角色职责 → 异常/分支约束（可选）→ 流程图占位；
                2. 描述「业务怎么走」，不写类/接口实现；可配合【此处插入XX流程图】；字数300-400字。
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
            1. 结构：%s可行性主张 → 依据（技术栈/成本/操作条件等事实）→ 明确结论与边界；
            2. 字数200字以内，结合项目实际技术栈与条件，禁止空泛「完全可行」口号；
            3. 结论写「可行」或「基本可行」，并一句说明限制条件。
            技术环境：%s
            """.formatted(title, type, ctx.envInfo());
    }

    // ==================== 第四章 系统设计 ====================

    private static String promptChapter4Section(String path, String bareTitle, String fullTitle, PromptContext ctx) {
        if (containsInTitles(bareTitle, fullTitle, "架构设计", "系统架构") && !containsInTitles(bareTitle, fullTitle, "结构")) {
            return """
                撰写「%s」，要求：
                1. 结构：总体架构选型主张 → 分层/端侧职责与技术选型依据 → 层间协作边界 → 架构图占位；
                2. 字数350字以内；说明为何采用该架构（与本系统规模/技术栈相关），禁止复述第三章功能清单；
                3. 插入占位：【此处插入系统架构图】；
                4. 结合开发环境：%s
                """.formatted(fullTitle, ctx.envInfo());
        }
        if (containsInTitles(bareTitle, fullTitle, "体系结构", "功能结构", "模块设计", "功能模块设计")) {
            return """
                撰写「%s」，要求：
                1. 结构：按端/角色划分模块 → 模块职责与协作关系 → 设计取舍说明 → 功能结构图占位；
                2. 按用户角色（如普通用户端、管理端）分节，每节约200字；证据落到具体模块名；
                3. 插入占位：【此处插入系统功能结构图】；勿写成实现代码或界面操作手册；
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
                1. 结构：库设计目标 → 概念/逻辑分层思路 → 核心表与关系概览（边界：不展开字段明细）；
                2. 300字以内；为后文 E-R 与表结构节铺垫，勿复述需求章用例。
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
            1. 结构：流程设计总主张 → 核心模块流程关系 → 与后文子流程节的分工边界；
            2. 150-250字；概括各核心功能模块操作流程及其相互关系；
            3. 段落叙述，不使用编号列表；不插入流程图占位（具体流程图在 4.3.x 子节呈现）。
            系统功能参考：%s
            """.formatted(title, ctx.tablesList());
    }

    private static String promptCh4FlowSubsection(String fullTitle, String bareTitle, PromptContext ctx) {
        String moduleName = StringUtils.isNotBlank(bareTitle) ? bareTitle.trim() : extractModuleName(fullTitle);
        return """
            撰写「%s」（系统流程设计），要求：
            1. 结构：流程目标主张 → 用户操作与系统处理步骤 → 校验/分支结果 → 流程图占位；
            2. 以段落描述「%s」业务流程，200-300字；语言精炼、学术规范；
            3. 禁止编号列表、分点罗列或小标题；禁止写成第五章代码实现；
            4. 段落后另起一行插入占位：【此处插入%s流程图】；
            5. 与论文题目「%s」及数据库表 %s 保持一致，禁止空泛套话。

            流程图说明（占位将由系统自动生成）：标准功能流程图，含开始/结束、处理步骤、条件判断分支。
            """.formatted(fullTitle, moduleName, moduleName, ctx.paperTitle(), ctx.tablesList());
    }

    private static String promptCh4_4_1(String fullTitle, PromptContext ctx) {
        return """
            撰写「%s」，要求：
            1. 结构：概念结构设计思路 → 总体 E-R 实体/联系说明 → 总体图占位 → 关联实体属性说明与属性图占位；
            2. 先用150-200字段落说明数据库概念结构（陈氏 E-R 表示法）的设计思路；
            3. 再用200-250字段落专门描述「系统总体 E-R 图」：逐一说明图中出现的实体、菱形联系及其 1/n 基数含义，只写存在外键关联的实体，不写无关联的孤立表；
            4. 禁止使用 PlantUML、Mermaid、代码块或 ASCII 图输出 ER 图；
            5. 上述文字段落后必须另起一行插入占位（括号与文字须完全一致，禁止改写）：
               【此处插入总体E-R图】
            6. 随后仅对下列「在总体 E-R 图中存在关联」的业务实体，各写80-120字属性说明，每段后插入对应占位（实体名须完全一致；不要为用户/管理员等等角色实体写属性图）：
            %s
            7. 段落叙述，禁止编号列表；与论文题目「%s」保持一致；勿复述第三章需求条文。
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
            1. 结构：表设计原则主张 → 核心表职责概览 → 逐表用途说明 + 三线表占位；
            2. 首段150-200字：说明数据库表设计原则（合理存储、外键关联、索引与约束、便于维护等），学术语气，段落叙述；
            3. 第二段80-120字：概括本系统约5～10张核心业务数据表及各自职责（不列字段明细）；
            4. 随后逐表输出（仅下列核心表，共5～10张，禁止字典/日志/中间关联表），每张表固定两段：
               a) 80-120字说明该表用途与关键约束，句末写「如表 4-Y 所示」（Y 从1递增）；
               b) 下一段独占一行插入占位（表名须完全一致）：
            %s
            5. 禁止自行输出 Markdown/HTML/ASCII 表格（禁止「字段名|类型|说明」等形式），表结构由系统按「字段名称、类型、长度、允许空值(Y/N)、主键(Y/N)、备注」六列自动插入；
            6. 禁止编号列表；勿复述 E-R 节已详述的联系说明；与论文题目「%s」保持一致；
            7. 正文叙述必须使用中文表名（如「用户信息表」），禁止出现 sys_xxx、tb_xxx 等 SQL 物理表名。
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
            1. 结构：模块在系统中的落点 → 关键调用链/方法逻辑 → 与中文表名的数据交互 → 界面截图占位（可选）；
            2. 结合论文题目与已上传项目代码，说明该模块如何落地，禁止空泛「完成了某某功能」；
            3. 必须使用段落方式叙述，连贯成文，不要使用编号列表、分点罗列或小标题；
            4. 全节字数控制在200字以内，语言精炼、学术规范；禁止词：%s；
            5. 须从代码中提炼真实的 Controller/Service/Mapper 调用链、核心方法或接口逻辑；缺代码时用简短承接说明，不得编造类名/方法名；
            6. 仅写实现层面内容，不得重复第三章需求分析或第四章设计中的功能描述与流程说明；
            7. 描述须与论文题目及本模块功能一致；涉及数据表时使用中文表名，禁止写 sys_xxx 等物理表名；
            8. 段落后可另起一行插入占位：【此处插入%s功能界面截图】，并说明截图应展示的主要界面元素。

            论文题目：%s
            代码参考：
            %s
            """.formatted(title, PaperWritingStandards.FORBIDDEN_WORDS, moduleName, ctx.paperTitle(), ctx.codeSnippet());
    }

    // ==================== 第六章 系统测试 ====================

    private static String promptCh6_1(PromptContext ctx) {
        return """
            撰写「6.1 测试目的」，要求：
            1. 结构：测试目标主张 → 范围与对象（功能/性能/体验）→ 与前文需求/实现的对应关系 → 预期可判定结论；
            2. 字数350-450字，学术语气；用例须可追溯到系统真实模块，勿写与题目无关的通用测试口号。
            """;
    }

    private static String promptCh6_2(PromptContext ctx) {
        return """
            撰写「6.2 测试环境与工具」，要求：
            1. 结构：环境配置表 → 一两句说明环境对测试结论的适用边界；
            2. 使用Markdown表格展示测试环境，列：类别|名称|版本/说明；
            3. 至少包含：操作系统、浏览器、开发/测试工具、数据库、后端框架等；名称写法与全文技术栈一致。
            开发环境：%s
            """.formatted(ctx.envInfo());
    }

    private static String promptCh6_3(PromptContext ctx) {
        return """
            撰写「6.3 测试过程」，要求：
            1. 结构：按功能模块分表 → 表前说明测了哪些能力 → 用例覆盖主成功与关键异常/边界（可）；
            2. 每个模块一张表，每张表至少8条用例；表格列：用例编号|用例名称|测试功能|输入数据|预期输出|测试结果；
            3. 测试功能必须来自系统实际模块（非虚构），输入/预期须具体可核对；
            4. 表标题格式：表6-X XXX功能测试用例。
            可测功能模块：%s
            """.formatted(ctx.tablesList());
    }

    private static String promptCh6_4(PromptContext ctx) {
        return """
            撰写「6.4 系统测试结论」，要求：
            1. 结构：总体是否达标主张 → 与测试过程结果对应的证据 → 发现的问题/局限（边界）→ 质量评价收束；
            2. 字数350-450字；结论须能被 6.3 用例结果支撑，禁止空喊「全面通过、完美无缺」。
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
                1. 结构：本章测试工作要点 → 主要结论 → 与后文总结章的衔接（一句即可）；
                2. 150-250字；与全章测试内容呼应，不要引入新测试项。
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
            1. 分两部分写清边界，勿混写：
               （一）研究结论 — 归纳本系统已完成的主要工作与可验证成果（对应前文模块/测试，写具体）；
               （二）不足与展望 — 明确本系统未覆盖的范围，并提出可落地的改进方向；
            2. 结论简练完整，不夸大「彻底解决」或「全面领先」；不足须真实对应毕设边界；
            3. 总字数800-1200字；禁止「综上所述」「具有重要意义」等套话收束。
            """;
    }

    private static String promptAcknowledgement(PromptContext ctx) {
        return """
            撰写「致谢」，要求：
            1. 以即将毕业的大学生视角，感谢导师、同学、学校、家人；
            2. 字数450字以内，情感真挚，语言朴实；
            3. 不要出现任何需人工填写的信息占位，禁止输出 [导师姓名]、[学校名称]、【导师】、XX 老师姓名、具体校名等占位写法；
            4. 统一用「导师」「指导老师」「母校」「各位授课老师」等泛称表述，不要编造真实姓名或学校名称。
            """;
    }

    private static String promptReferencesPage(PromptContext ctx) {
        return """
            生成「参考文献」章节，要求：
            1. 按 GB/T 7714 格式列出全部参考文献，带序号[1][2]...；
            2. 直接使用以下文献列表输出，不要编造新文献；
            3. 不要在章节开头添加核实提示或其他说明文字，只输出文献列表。
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
