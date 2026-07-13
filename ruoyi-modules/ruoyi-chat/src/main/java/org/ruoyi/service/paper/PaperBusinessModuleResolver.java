package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 SQL 表名挑选关键业务模块，用于第五章「系统实现」大纲。
 */
public final class PaperBusinessModuleResolver {

    private static final Pattern INFRA_TABLE = Pattern.compile(
        "^(qrtz_|act_|gen_|flyway_|schema_version|databasechangelog)",
        Pattern.CASE_INSENSITIVE);

    private static final Set<String> SKIP_EXACT = Set.of(
        "sys_config", "sys_dict_data", "sys_dict_type", "sys_job", "sys_job_log",
        "sys_logininfor", "sys_oper_log", "gen_table", "gen_table_column");

    private PaperBusinessModuleResolver() {
    }

    public record BusinessModuleGroups(List<String> adminFunctions, List<String> userFunctions) {
    }

    /**
     * 挑选关键业务模块：管理员侧 / 用户侧，各最多 {@code maxPerSide} 项。
     */
    public static BusinessModuleGroups resolve(List<String> tables, int maxPerSide) {
        return resolve(tables, null, null, null, maxPerSide);
    }

    public static BusinessModuleGroups resolve(List<String> tables, PaperSession.SqlParsed sqlParsed, int maxPerSide) {
        return resolve(tables, sqlParsed, null, null, maxPerSide);
    }

    public static BusinessModuleGroups resolve(List<String> tables, PaperSession.SqlParsed sqlParsed,
                                               String paperTitle, String summary, int maxPerSide) {
        if (tables == null || tables.isEmpty()) {
            return new BusinessModuleGroups(
                List.of("用户管理功能", "菜单管理功能"),
                buildUserFallback(List.of(), List.of("用户管理功能", "菜单管理功能"),
                    paperTitle, summary, tables, sqlParsed, Math.max(3, Math.min(maxPerSide, 10)))
            );
        }
        int cap = Math.max(3, Math.min(maxPerSide, 10));
        Set<String> adminSeen = new LinkedHashSet<>();
        Set<String> userSeen = new LinkedHashSet<>();
        List<String> admin = new ArrayList<>();
        List<String> user = new ArrayList<>();

        for (String table : tables) {
            if (isInfrastructureTable(table)) {
                continue;
            }
            String module = PaperModuleDictionary.inferModuleName(table);
            if (isStrictAdminTable(table, module)) {
                String func = toAdminFunctionName(module, table, sqlParsed);
                if (!isVagueFunctionName(func) && adminSeen.add(normalizeKey(func)) && admin.size() < cap) {
                    admin.add(func);
                }
            } else {
                String userFunc = toUserFunctionName(module, table, sqlParsed);
                if (!isVagueFunctionName(userFunc) && userSeen.add(normalizeKey(userFunc)) && user.size() < cap) {
                    user.add(userFunc);
                }
                String adminFunc = toAdminFunctionName(module, table, sqlParsed);
                if (!isVagueFunctionName(adminFunc) && adminSeen.add(normalizeKey(adminFunc)) && admin.size() < cap) {
                    admin.add(adminFunc);
                }
            }
        }

        if (admin.isEmpty()) {
            admin.add("用户管理功能");
            admin.add("角色管理功能");
        }
        if (user.isEmpty()) {
            user.addAll(buildUserFallback(user, admin, paperTitle, summary, tables, sqlParsed, cap));
        }
        user = finalizeUserFunctions(user, admin, paperTitle, summary, tables, sqlParsed, cap);
        return new BusinessModuleGroups(List.copyOf(admin), user);
    }

    /** 过滤空泛功能名；仍为空时从管理员侧业务模块/流程/题目关键词补全 */
    public static List<String> finalizeUserFunctions(List<String> user, List<String> admin,
                                                     String paperTitle, String summary,
                                                     List<String> tables, PaperSession.SqlParsed sqlParsed,
                                                     int cap) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String name : user) {
            if (!isVagueFunctionName(name) && seen.add(normalizeKey(name))) {
                result.add(name);
            }
        }
        if (result.isEmpty()) {
            result.addAll(buildUserFallback(result, admin, paperTitle, summary, tables, sqlParsed, cap));
            result.removeIf(PaperBusinessModuleResolver::isVagueFunctionName);
        }
        return List.copyOf(result.stream().limit(cap).toList());
    }

    /**
     * 挑选 3～5 个核心业务流程模块，用于 4.2 系统流程设计子节。
     */
    public static List<String> resolveFlowModules(List<String> tables, int minCount, int maxCount) {
        return resolveFlowModules(tables, null, null, minCount, maxCount);
    }

    /**
     * 结合 SQL 表、论文题目与功能摘要，挑选 3～5 个核心业务流程模块。
     * <p>
     * 流程名由「表/模块推断出的业务核心词 + 语义动词模板」动态组成，不写死具体业务功能。
     */
    public static List<String> resolveFlowModules(List<String> tables, String paperTitle, String summary,
                                                  int minCount, int maxCount) {
        return resolveFlowModules(tables, paperTitle, summary, null, minCount, maxCount);
    }

    public static List<String> resolveFlowModules(List<String> tables, String paperTitle, String summary,
                                                  PaperSession.SqlParsed sqlParsed,
                                                  int minCount, int maxCount) {
        int min = Math.max(3, minCount);
        int max = Math.max(min, Math.min(maxCount, 5));
        List<String> titleKeywords = extractTitleKeywords(paperTitle);
        List<String> summaryModules = parseSummaryModules(summary);
        String titleCorpus = buildTitleCorpus(paperTitle, titleKeywords);
        LinkedHashSet<String> flows = new LinkedHashSet<>();

        List<FlowSource> userSources = collectUserFlowSources(tables, summaryModules, titleKeywords, titleCorpus, sqlParsed);
        List<FlowSource> adminSources = collectAdminFlowSources(tables, summaryModules, titleKeywords, titleCorpus, sqlParsed);

        if (hasAuthEntry(userSources, tables)) {
            String authFlow = buildAuthFlowLabel(userSources, titleCorpus);
            if (acceptFlowCandidate(authFlow, flows, false)) {
                flows.add(authFlow);
            }
        }

        for (FlowSource source : userSources) {
            if (flows.size() >= max) {
                break;
            }
            String flow = buildWriteFlowLabel(source.core(), source.corpus(), false);
            if (acceptFlowCandidate(flow, flows, false) && !isAuthFlowName(flow)) {
                flows.add(flow);
            }
        }

        if (flows.size() < max && !adminSources.isEmpty()) {
            FlowSource admin = adminSources.get(0);
            String adminFlow = buildWriteFlowLabel(admin.core(), admin.corpus(), true);
            if (acceptFlowCandidate(adminFlow, flows, false)) {
                flows.add(adminFlow);
            }
        }

        for (String keyword : titleKeywords) {
            if (flows.size() >= min) {
                break;
            }
            String flow = buildWriteFlowLabel(keyword, keyword + " " + titleCorpus, false);
            if (acceptFlowCandidate(flow, flows, true)) {
                flows.add(flow);
            }
        }

        if (flows.size() < min) {
            for (FlowSource source : userSources) {
                if (flows.size() >= min) {
                    break;
                }
                String fallback = "办理" + source.core() + "业务";
                if (acceptFlowCandidate(fallback, flows, true)) {
                    flows.add(fallback);
                }
            }
        }

        return flows.stream().limit(max).toList();
    }

    private record FlowSource(String core, String corpus, int score) {
    }

    private static String buildTitleCorpus(String paperTitle, List<String> titleKeywords) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(paperTitle)) {
            sb.append(paperTitle.trim()).append(' ');
        }
        for (String keyword : titleKeywords) {
            sb.append(keyword).append(' ');
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static List<FlowSource> collectUserFlowSources(List<String> tables, List<String> summaryModules,
                                                           List<String> titleKeywords, String titleCorpus,
                                                           PaperSession.SqlParsed sqlParsed) {
        LinkedHashMap<String, FlowSource> map = new LinkedHashMap<>();
        if (tables != null) {
            for (String table : tables) {
                if (isInfrastructureTable(table)) {
                    continue;
                }
                String module = PaperModuleDictionary.inferModuleName(table);
                if (isStrictAdminTable(table, module) || isInfrastructureFlowModule(module, table)) {
                    continue;
                }
                mergeFlowSource(map, table, module, table + " " + StringUtils.defaultString(module) + " " + titleCorpus,
                    scoreFlowSource(module, table, titleKeywords, summaryModules, false, sqlParsed), sqlParsed);
            }
        }
        for (String module : summaryModules) {
            if (isAdminModuleName(module) || isInfrastructureFlowModule(module, null)) {
                continue;
            }
            String core = extractBusinessCore(module, null, sqlParsed);
            if (StringUtils.isBlank(core)) {
                continue;
            }
            mergeFlowSource(map, core, module, module + " " + titleCorpus,
                scoreFlowSource(module, null, titleKeywords, summaryModules, false, sqlParsed), sqlParsed);
        }
        return map.values().stream()
            .sorted(Comparator.comparingInt(FlowSource::score).reversed())
            .toList();
    }

    private static List<FlowSource> collectAdminFlowSources(List<String> tables, List<String> summaryModules,
                                                            List<String> titleKeywords, String titleCorpus,
                                                            PaperSession.SqlParsed sqlParsed) {
        LinkedHashMap<String, FlowSource> map = new LinkedHashMap<>();
        if (tables != null) {
            for (String table : tables) {
                if (isInfrastructureTable(table)) {
                    continue;
                }
                String module = PaperModuleDictionary.inferModuleName(table);
                if (!isStrictAdminTable(table, module)) {
                    continue;
                }
                mergeFlowSource(map, table, module, table + " " + StringUtils.defaultString(module) + " " + titleCorpus,
                    scoreFlowSource(module, table, titleKeywords, summaryModules, true, sqlParsed), sqlParsed);
            }
        }
        for (String module : summaryModules) {
            if (!isAdminModuleName(module)) {
                continue;
            }
            String core = extractBusinessCore(module, null, sqlParsed);
            if (StringUtils.isBlank(core)) {
                continue;
            }
            mergeFlowSource(map, core, module, module + " " + titleCorpus,
                scoreFlowSource(module, null, titleKeywords, summaryModules, true, sqlParsed), sqlParsed);
        }
        return map.values().stream()
            .sorted(Comparator.comparingInt(FlowSource::score).reversed())
            .toList();
    }

    private static void mergeFlowSource(Map<String, FlowSource> map, String keyHint, String module,
                                        String corpus, int score, PaperSession.SqlParsed sqlParsed) {
        String core = extractBusinessCore(module, keyHint, sqlParsed);
        if (StringUtils.isBlank(core)) {
            return;
        }
        String mapKey = normalizeKey(core);
        FlowSource incoming = new FlowSource(core, corpus.toLowerCase(Locale.ROOT), score);
        FlowSource existing = map.get(mapKey);
        if (existing == null || incoming.score() > existing.score()) {
            map.put(mapKey, incoming);
        }
    }

    private static int scoreFlowSource(String module, String table, List<String> titleKeywords,
                                       List<String> summaryModules, boolean adminSide,
                                       PaperSession.SqlParsed sqlParsed) {
        String core = extractBusinessCore(module, table, sqlParsed);
        int score = relevanceScore(core, titleKeywords, summaryModules);
        if (StringUtils.isNotBlank(table) && StringUtils.isNotBlank(module)) {
            score += 30;
        }
        if (adminSide && isAdminModuleName(StringUtils.defaultString(module))) {
            score += 20;
        }
        String preview = buildWriteFlowLabel(core, StringUtils.defaultString(module) + " " + StringUtils.defaultString(table), adminSide);
        if (isQueryOnlyFlow(preview)) {
            score -= 200;
        }
        return score;
    }

    /** 从模块名/表名提取业务核心词（优先表注释与中文模块词典） */
    private static String extractBusinessCore(String moduleLabel, String tableHint, PaperSession.SqlParsed sqlParsed) {
        if (StringUtils.isNotBlank(tableHint)) {
            String fromTable = PaperTableLabelResolver.resolveEntityLabel(tableHint, sqlParsed);
            if (PaperTableLabelResolver.isChineseLabel(fromTable)) {
                return stripBusinessAffixes(fromTable);
            }
        }
        if (StringUtils.isNotBlank(moduleLabel) && PaperTableLabelResolver.isChineseLabel(moduleLabel)) {
            String core = stripBusinessAffixes(moduleLabel);
            if (StringUtils.isNotBlank(core)) {
                return core;
            }
        }
        if (StringUtils.isNotBlank(tableHint)) {
            String inferred = PaperModuleDictionary.inferModuleName(tableHint);
            if (StringUtils.isNotBlank(inferred)) {
                return stripBusinessAffixes(inferred);
            }
        }
        if (StringUtils.isNotBlank(moduleLabel)) {
            return stripBusinessAffixes(moduleLabel);
        }
        return "";
    }

    private static String stripBusinessAffixes(String name) {
        if (StringUtils.isBlank(name)) {
            return "";
        }
        String value = name.trim()
            .replace("管理功能", "")
            .replace("功能模块", "")
            .replace("模块", "")
            .replace("功能", "")
            .replace("管理", "")
            .replace("信息", "")
            .trim();
        if (value.length() < 2) {
            return name.trim().replace("功能", "").trim();
        }
        return value;
    }

    private static boolean isInfrastructureFlowModule(String module, String table) {
        if (isLowValueFlowSource(StringUtils.defaultString(module))) {
            return true;
        }
        if (StringUtils.isBlank(table)) {
            return false;
        }
        String lower = table.toLowerCase(Locale.ROOT);
        return lower.contains("dict") || lower.contains("config") || lower.contains("log")
            || lower.contains("menu") || lower.contains("role") || lower.contains("permission");
    }

    private static boolean hasAuthEntry(List<FlowSource> userSources, List<String> tables) {
        if (userSources.stream().anyMatch(s -> isAuthCorpus(s.corpus()) || isAuthCorpus(s.core()))) {
            return true;
        }
        if (tables == null) {
            return false;
        }
        return tables.stream().anyMatch(t -> {
            String lower = t.toLowerCase(Locale.ROOT);
            return lower.contains("user") || lower.contains("member") || lower.contains("account");
        });
    }

    private static String buildAuthFlowLabel(List<FlowSource> userSources, String titleCorpus) {
        for (FlowSource source : userSources) {
            if (isAuthCorpus(source.corpus()) || isAuthCorpus(source.core())) {
                return buildWriteFlowLabel(source.core(), source.corpus(), false);
            }
        }
        if (titleCorpus.contains("注册") || titleCorpus.contains("登录")) {
            return "用户注册登录";
        }
        return "用户注册登录";
    }

    private static boolean isAuthCorpus(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("login") || lower.contains("register") || lower.contains("auth")
            || lower.contains("注册") || lower.contains("登录")
            || lower.contains("user") || lower.contains("member") || lower.contains("account");
    }

    private static boolean isAuthFlowName(String flow) {
        return StringUtils.isNotBlank(flow) && (flow.contains("注册") || flow.contains("登录"));
    }

    /**
     * 根据业务核心词 + 表/模块/题目语义，选择「增改类」动词并拼接流程名。
     */
    private static String buildWriteFlowLabel(String core, String corpus, boolean adminSide) {
        if (StringUtils.isBlank(core)) {
            return "";
        }
        String text = (core + " " + StringUtils.defaultString(corpus)).toLowerCase(Locale.ROOT);
        WriteVerb verb = inferWriteVerb(core, text);
        if (adminSide) {
            return formatAdminFlowLabel(core, verb);
        }
        return formatUserFlowLabel(core, verb);
    }

    private enum WriteVerb {
        AUTH, ENROLL, RESERVE, BORROW, PAY, MODIFY, AUDIT, PUBLISH, SUBMIT, MAINTAIN
    }

    private static WriteVerb inferWriteVerb(String core, String text) {
        if (matchesSemantic(text, "login", "register", "auth", "注册", "登录", "account", "member", "password")) {
            return WriteVerb.AUTH;
        }
        if (matchesSemantic(text, "signup", "enroll", "apply", "entry", "报名", "register_")) {
            return WriteVerb.ENROLL;
        }
        if (matchesSemantic(text, "appointment", "booking", "reserve", "预约", "seat")) {
            return WriteVerb.RESERVE;
        }
        if (matchesSemantic(text, "borrow", "lend", "loan", "借阅")) {
            return WriteVerb.BORROW;
        }
        if (matchesSemantic(text, "pay", "payment", "bill", "支付", "wallet")) {
            return WriteVerb.PAY;
        }
        if (matchesSemantic(text, "audit", "approve", "review", "审核", "审批")) {
            return WriteVerb.AUDIT;
        }
        if (matchesSemantic(text, "profile", "my_", "个人", "我的", "address", "password", "修改")) {
            return WriteVerb.MODIFY;
        }
        if (matchesSemantic(text, "post", "article", "news", "blog", "content", "comment", "reply",
            "文章", "资讯", "新闻", "评论", "帖子", "内容")) {
            return WriteVerb.PUBLISH;
        }
        return WriteVerb.SUBMIT;
    }

    private static boolean matchesSemantic(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String formatUserFlowLabel(String core, WriteVerb verb) {
        return switch (verb) {
            case AUTH -> (core.contains("用户") || core.contains("会员")) ? "用户注册登录" : core + "注册登录";
            case ENROLL -> core + "报名";
            case RESERVE -> core + "预约";
            case BORROW -> core + "借阅";
            case PAY -> core + "支付";
            case MODIFY -> "修改" + core + "信息";
            case PUBLISH -> "提交" + core;
            case SUBMIT -> "提交" + core;
            default -> "提交" + core;
        };
    }

    private static String formatAdminFlowLabel(String core, WriteVerb verb) {
        if (verb == WriteVerb.AUDIT) {
            return core + "审核";
        }
        if (core.contains("用户") || core.contains("会员")) {
            return "用户信息维护";
        }
        return core + "信息维护";
    }

    /** 流程子模块优先增删改，尽量不生成纯查询类 */
    private static boolean acceptFlowCandidate(String flow, Set<String> flows, boolean allowQueryFallback) {
        if (StringUtils.isBlank(flow) || isDuplicateFlow(flows, flow)) {
            return false;
        }
        if (!isQueryOnlyFlow(flow)) {
            return true;
        }
        return allowQueryFallback && flows.stream().noneMatch(PaperBusinessModuleResolver::isQueryOnlyFlow);
    }

    private static boolean isQueryOnlyFlow(String name) {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        return name.startsWith("查看")
            || name.startsWith("查询")
            || name.startsWith("浏览")
            || name.contains("信息查询")
            || name.endsWith("列表");
    }

    /** 字典/日志/菜单等系统表，不作为业务流程小节 */
    private static boolean isLowValueFlowSource(String moduleName) {
        if (StringUtils.isBlank(moduleName)) {
            return true;
        }
        String text = moduleName.toLowerCase(Locale.ROOT);
        return text.contains("字典")
            || text.contains("配置")
            || text.contains("日志")
            || text.contains("轮播")
            || text.contains("banner")
            || text.contains("菜单")
            || text.contains("权限")
            || text.contains("角色");
    }

    private static List<String> parseSummaryModules(String summary) {
        if (StringUtils.isBlank(summary)) {
            return List.of();
        }
        Matcher matcher = Pattern.compile("包含(.+?)等模块").matcher(summary);
        if (matcher.find()) {
            return Arrays.stream(matcher.group(1).split("[、,，;；]"))
                .map(String::trim)
                .filter(s -> s.length() >= 2 && s.length() <= 16)
                .toList();
        }
        return Arrays.stream(summary.split("[、,，;；\\n]"))
            .map(s -> s.replaceAll("本系统包含|等模块|模块|功能", "").trim())
            .filter(s -> s.length() >= 2 && s.length() <= 16)
            .limit(8)
            .toList();
    }

    private static List<String> extractTitleKeywords(String paperTitle) {
        if (StringUtils.isBlank(paperTitle)) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        String title = paperTitle.trim();

        String[] domainTerms = {
            "骑行", "图书", "借阅", "座位", "选课", "成绩", "课程", "订单", "商品",
            "购物", "支付", "考勤", "薪资", "酒店", "房间", "票务", "医疗", "患者",
            "活动", "预约", "校园", "电商", "超市", "零食", "论坛", "博客", "新闻"
        };
        for (String term : domainTerms) {
            if (title.contains(term)) {
                keywords.add(term);
            }
        }

        String cleaned = title
            .replaceAll("基于|的设计与实现|设计与实现|系统|平台|网站|小程序|应用|管理信息系统|信息管理系统", "")
            .replaceAll("[A-Za-z0-9._-]+", " ")
            .trim();
        for (String part : cleaned.split("[\\s的与及和]+")) {
            if (part.length() >= 2 && part.length() <= 8) {
                keywords.add(part);
            }
        }
        return keywords.stream().limit(6).toList();
    }

    private static int relevanceScore(String name, List<String> titleKeywords, List<String> summaryModules) {
        if (StringUtils.isBlank(name)) {
            return 0;
        }
        int score = 0;
        String key = normalizeKey(name);
        for (int i = 0; i < summaryModules.size(); i++) {
            String summaryKey = normalizeKey(summaryModules.get(i));
            if (key.contains(summaryKey) || summaryKey.contains(key)) {
                score += 100 - i * 10;
            }
        }
        for (String keyword : titleKeywords) {
            String keywordKey = normalizeKey(keyword);
            if (key.contains(keywordKey) || name.contains(keyword)) {
                score += 50;
            }
        }
        return score;
    }

    private static boolean isAdminModuleName(String moduleName) {
        if (StringUtils.isBlank(moduleName)) {
            return false;
        }
        return moduleName.contains("用户管理")
            || moduleName.contains("成员管理")
            || moduleName.contains("角色管理")
            || moduleName.contains("菜单管理")
            || moduleName.contains("权限管理")
            || moduleName.contains("系统配置")
            || moduleName.contains("管理员");
    }

    private static boolean isDuplicateFlow(Set<String> flows, String name) {
        String key = normalizeKey(name);
        for (String existing : flows) {
            if (normalizeKey(existing).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String toFlowModuleName(String funcName) {
        if (StringUtils.isBlank(funcName)) {
            return "";
        }
        String name = funcName.trim()
            .replace("管理功能", "管理")
            .replace("功能", "")
            .trim();
        if (name.endsWith("管理") && name.length() > 2) {
            return name;
        }
        if (name.contains("注册") || name.contains("登录")) {
            return "用户注册登录";
        }
        return name;
    }

    public static String resolveUserRoleLabel(String title) {
        if (StringUtils.isBlank(title)) {
            return "用户";
        }
        if (title.contains("图书") || title.contains("图书馆")) {
            return "读者";
        }
        return "用户";
    }

    private static boolean isInfrastructureTable(String table) {
        String lower = table.toLowerCase(Locale.ROOT);
        if (INFRA_TABLE.matcher(lower).find()) {
            return true;
        }
        if (SKIP_EXACT.contains(lower)) {
            return true;
        }
        return lower.endsWith("_log") || lower.contains("_log_");
    }

    /**
     * 仅将平台管理/RBAC 表归入管理员侧；业务表（钱包、骑行、评论等）归用户侧，避免用户小节为空后落入「核心业务功能」。
     */
    private static boolean isStrictAdminTable(String table, String module) {
        String lower = table.toLowerCase(Locale.ROOT);
        if (lower.contains("my_")) {
            return false;
        }
        if (module != null && module.contains("我的")) {
            return false;
        }
        if (lower.startsWith("sys_")) {
            return true;
        }
        if (lower.contains("admin") && !lower.contains("comment")) {
            return true;
        }
        return isRbacOrConfigTable(lower);
    }

    private static boolean isRbacOrConfigTable(String lower) {
        return lower.equals("role") || lower.equals("roles") || lower.endsWith("_role")
            || lower.equals("menu") || lower.equals("menus") || lower.endsWith("_menu")
            || lower.contains("dept") || lower.contains("department")
            || lower.contains("permission") || lower.contains("dict")
            || (lower.contains("config") && !lower.contains("home"));
    }

    /** 用户侧为空时，从业务管理项/流程/题目关键词推断具体功能名，禁止「核心业务功能」等空泛词 */
    private static List<String> buildUserFallback(List<String> existingUser, List<String> adminFunctions,
                                                  String paperTitle, String summary, List<String> tables,
                                                  PaperSession.SqlParsed sqlParsed, int cap) {
        Set<String> seen = new LinkedHashSet<>();
        for (String name : existingUser) {
            seen.add(normalizeKey(name));
        }
        List<String> result = new ArrayList<>(existingUser);

        for (String adminFunc : adminFunctions) {
            if (result.size() >= cap) {
                break;
            }
            if (isSysAdminFunction(adminFunc)) {
                continue;
            }
            String converted = manageFunctionToUserFunction(adminFunc);
            if (!isVagueFunctionName(converted) && seen.add(normalizeKey(converted))) {
                result.add(converted);
            }
        }

        if (result.size() < 3) {
            List<String> flows = resolveFlowModules(tables, paperTitle, summary, sqlParsed, 3, 5);
            for (String flow : flows) {
                if (result.size() >= cap) {
                    break;
                }
                String func = flowLabelToUserFunction(flow);
                if (!isVagueFunctionName(func) && seen.add(normalizeKey(func))) {
                    result.add(func);
                }
            }
        }

        if (result.isEmpty()) {
            for (String keyword : extractTitleKeywords(paperTitle)) {
                if (result.size() >= cap) {
                    break;
                }
                String func = keyword + "功能";
                if (!isVagueFunctionName(func) && seen.add(normalizeKey(func))) {
                    result.add(func);
                }
            }
        }

        if (result.isEmpty()) {
            for (String module : parseSummaryModules(summary)) {
                if (result.size() >= cap) {
                    break;
                }
                if (isAdminModuleName(module)) {
                    continue;
                }
                String func = toUserFunctionName(module, null, sqlParsed);
                if (!isVagueFunctionName(func) && seen.add(normalizeKey(func))) {
                    result.add(func);
                }
            }
        }

        return List.copyOf(result);
    }

    private static boolean isSysAdminFunction(String funcName) {
        if (StringUtils.isBlank(funcName)) {
            return true;
        }
        return funcName.contains("角色")
            || funcName.contains("菜单")
            || funcName.contains("权限")
            || funcName.contains("部门")
            || funcName.contains("字典")
            || funcName.contains("配置")
            || funcName.contains("日志")
            || funcName.contains("用户管理")
            || funcName.contains("系统配置");
    }

    private static String manageFunctionToUserFunction(String adminFunc) {
        if (StringUtils.isBlank(adminFunc)) {
            return "";
        }
        String core = adminFunc
            .replace("管理功能", "")
            .replace("功能", "")
            .trim();
        if (StringUtils.isBlank(core) || isVagueFunctionName(core)) {
            return "";
        }
        return core + "功能";
    }

    private static String flowLabelToUserFunction(String flow) {
        if (StringUtils.isBlank(flow)) {
            return "";
        }
        if (flow.contains("注册") || flow.contains("登录")) {
            return "注册登录功能";
        }
        String core = flow
            .replace("提交", "")
            .replace("办理", "")
            .replace("修改", "")
            .replace("信息", "")
            .trim();
        core = stripBusinessAffixes(core);
        if (StringUtils.isBlank(core)) {
            return "";
        }
        return core.endsWith("功能") ? core : core + "功能";
    }

    public static boolean isVagueFunctionName(String name) {
        if (StringUtils.isBlank(name)) {
            return true;
        }
        String text = name.replaceAll("\\s+", "");
        return text.contains("核心业务")
            || text.contains("业务核心")
            || text.equals("业务功能")
            || text.equals("业务")
            || text.equals("核心功能")
            || text.equals("主要功能")
            || text.equals("系统功能");
    }

    private static String toAdminFunctionName(String module, String table, PaperSession.SqlParsed sqlParsed) {
        String core = resolveModuleCoreLabel(module, table, sqlParsed);
        if (core.endsWith("管理")) {
            return core.endsWith("功能") ? core : core + "功能";
        }
        return core + "管理功能";
    }

    private static String toUserFunctionName(String module, String table, PaperSession.SqlParsed sqlParsed) {
        String core = resolveModuleCoreLabel(module, table, sqlParsed);
        String lower = table == null ? "" : table.toLowerCase(Locale.ROOT);
        if (lower.contains("my_") || core.contains("我的") || (module != null && module.contains("我的"))) {
            String bare = core.replace("我的", "").trim();
            return "我的" + bare + "功能";
        }
        if (core.endsWith("功能")) {
            return core;
        }
        return core + "功能";
    }

    private static String resolveModuleCoreLabel(String module, String table, PaperSession.SqlParsed sqlParsed) {
        if (StringUtils.isNotBlank(table)) {
            String entity = PaperTableLabelResolver.resolveEntityLabel(table, sqlParsed);
            if (PaperTableLabelResolver.isChineseLabel(entity)) {
                return stripBusinessAffixes(entity);
            }
        }
        if (StringUtils.isNotBlank(module) && PaperTableLabelResolver.isChineseLabel(module)) {
            return stripBusinessAffixes(module);
        }
        if (StringUtils.isNotBlank(module)) {
            return stripBusinessAffixes(module);
        }
        return "业务";
    }

    private static String normalizeKey(String name) {
        return name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
