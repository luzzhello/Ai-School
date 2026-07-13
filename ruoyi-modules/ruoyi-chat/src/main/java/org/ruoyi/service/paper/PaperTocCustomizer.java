package org.ruoyi.service.paper;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.TocNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据论文题目、SQL 等业务信息补全默认大纲（第二章 xxx、第五章功能模块等）。
 */
@Slf4j
@Component
public class PaperTocCustomizer {

    private static final Pattern XXX = Pattern.compile("(?i)xxx");

    private static final Pattern SECTION_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)+?)\\s*");

    /**
     * 补全大纲并重新分配节点 id。
     */
    public void customize(List<TocNode> toc, PaperSession session) {
        if (toc == null || toc.isEmpty() || session == null) {
            return;
        }
        fillChapter2Technologies(toc, session);
        rebuildChapter4FlowModules(toc, session);
        refreshChapter5Modules(toc, session);
        PaperTocNodeIds.assign(toc, "");
        log.info("大纲已按题目/SQL 补全, title={}", session.getTitle());
    }

    /**
     * 仅刷新第五章「系统实现」子模块（SQL 解析后或手动修复时调用）。
     */
    public void refreshChapter5Modules(List<TocNode> toc, PaperSession session) {
        rebuildChapter5Modules(toc, session);
    }

    private void fillChapter2Technologies(List<TocNode> toc, PaperSession session) {
        TocNode ch2 = findNodeByKeyword(toc, "关键技术");
        if (ch2 == null) {
            ch2 = findNodeByKeyword(toc, "相关技术");
        }
        if (ch2 == null || ch2.getChildren() == null) {
            return;
        }
        TechStack stack = TechStack.resolve(session);
        int introIndex = 0;
        for (TocNode child : ch2.getChildren()) {
            String title = child.getTitle();
            if (title == null || !title.toLowerCase(Locale.ROOT).contains("xxx")) {
                continue;
            }
            String replacement = stack.resolveForTitle(title, introIndex);
            introIndex++;
            child.setTitle(XXX.matcher(title).replaceAll(replacement));
        }
    }

    private void rebuildChapter4FlowModules(List<TocNode> toc, PaperSession session) {
        TocNode ch4Flow = findFlowDesignNode(toc);
        if (ch4Flow == null) {
            return;
        }
        List<String> tables = session.getSqlParsed() == null ? List.of() : session.getSqlParsed().getTables();
        String summary = session.getSqlParsed() == null ? null : session.getSqlParsed().getSummary();
        List<String> modules = PaperBusinessModuleResolver.resolveFlowModules(
            tables, session.getTitle(), summary, session.getSqlParsed(), 3, 5);

        String sectionPrefix = extractSectionPrefix(ch4Flow.getTitle());
        List<TocNode> children = new ArrayList<>();
        int index = 1;
        for (String module : modules) {
            children.add(leafNode(sectionPrefix + "." + index + " " + module, 3));
            index++;
        }
        ch4Flow.setChildren(children);
        log.info("系统流程设计子模块已生成, prefix={}, count={}", sectionPrefix, children.size());
    }

    /**
     * 查找「系统流程设计」节点（排除数据库等含「流程」字样的误判）。
     */
    static TocNode findFlowDesignNode(List<TocNode> toc) {
        TocNode node = findNodeByTitleKeyword(toc, "系统流程设计");
        if (node != null) {
            return node;
        }
        return findNodeByPredicate(toc, PaperTocCustomizer::isFlowDesignTitle);
    }

    static boolean isFlowDesignTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return false;
        }
        if (title.contains("数据库") || title.contains("E-R") || title.contains("ER图")) {
            return false;
        }
        return title.contains("系统流程设计")
            || title.contains("系统流程")
            || (title.contains("流程设计") && title.contains("系统"));
    }

    static String extractSectionPrefix(String title) {
        if (StringUtils.isBlank(title)) {
            return "4.3";
        }
        Matcher matcher = SECTION_PREFIX.matcher(title.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "4.3";
    }

    private static TocNode findNodeByPredicate(List<TocNode> nodes, java.util.function.Predicate<String> predicate) {
        if (nodes == null) {
            return null;
        }
        for (TocNode node : nodes) {
            if (node.getTitle() != null && predicate.test(node.getTitle())) {
                return node;
            }
            TocNode found = findNodeByPredicate(node.getChildren(), predicate);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void rebuildChapter5Modules(List<TocNode> toc, PaperSession session) {
        TocNode ch5 = findNodeByTitleKeyword(toc, "系统实现");
        if (ch5 == null) {
            return;
        }
        List<String> tables = session.getSqlParsed() == null ? List.of() : session.getSqlParsed().getTables();
        String summary = session.getSqlParsed() == null ? null : session.getSqlParsed().getSummary();
        PaperBusinessModuleResolver.BusinessModuleGroups groups = PaperBusinessModuleResolver.resolve(
            tables, session.getSqlParsed(), session.getTitle(), summary, 8);
        List<String> adminFuncs = groups.adminFunctions().stream()
            .filter(f -> !PaperBusinessModuleResolver.isVagueFunctionName(f))
            .toList();
        List<String> userFuncs = PaperBusinessModuleResolver.finalizeUserFunctions(
            groups.userFunctions(), adminFuncs, session.getTitle(), summary, tables, session.getSqlParsed(), 8);
        String userLabel = PaperBusinessModuleResolver.resolveUserRoleLabel(session.getTitle());

        List<TocNode> children = new ArrayList<>();

        TocNode adminBranch = branchNode("5.1 管理员功能模块", 2);
        List<TocNode> adminLeaves = new ArrayList<>();
        int adminIndex = 1;
        for (String func : adminFuncs) {
            adminLeaves.add(leafNode("5.1." + adminIndex + " " + func, 3));
            adminIndex++;
        }
        adminBranch.setChildren(adminLeaves);
        children.add(adminBranch);

        TocNode userBranch = branchNode("5.2 " + userLabel + "功能模块", 2);
        List<TocNode> userLeaves = new ArrayList<>();
        int userIndex = 1;
        for (String func : userFuncs) {
            userLeaves.add(leafNode("5.2." + userIndex + " " + func, 3));
            userIndex++;
        }
        userBranch.setChildren(userLeaves);
        children.add(userBranch);

        children.add(leafNode("5.3 本章小结", 2));
        ch5.setChildren(children);
    }

    private TocNode leafNode(String title, int level) {
        TocNode node = new TocNode();
        node.setTitle(title);
        node.setLevel(level);
        node.setStatus("pending");
        node.setGenerated(false);
        node.setChildren(new ArrayList<>());
        return node;
    }

    private TocNode branchNode(String title, int level) {
        TocNode node = leafNode(title, level);
        node.setChildren(new ArrayList<>());
        return node;
    }

    private static TocNode findNodeByKeyword(List<TocNode> nodes, String keyword) {
        if (nodes == null) {
            return null;
        }
        for (TocNode node : nodes) {
            if (node.getTitle() != null && node.getTitle().contains(keyword)) {
                return node;
            }
            TocNode found = findNodeByKeyword(node.getChildren(), keyword);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static TocNode findNodeByTitleKeyword(List<TocNode> nodes, String keyword) {
        return findNodeByKeyword(nodes, keyword);
    }

    /**
     * 从题目、环境信息、代码中推断技术栈。
     */
    private static final class TechStack {
        private final String language;
        private final String backend;
        private final String frontend;
        private final String database;

        private TechStack(String language, String backend, String frontend, String database) {
            this.language = language;
            this.backend = backend;
            this.frontend = frontend;
            this.database = database;
        }

        static TechStack resolve(PaperSession session) {
            String corpus = buildCorpus(session);
            String language = firstMatch(corpus, "Java", "Python", "C#", "Go", "PHP");
            if (language == null) {
                language = containsAny(corpus, "spring", "mybatis", "jdk") ? "Java" : "Java";
            }
            String backend = firstMatch(corpus, "SpringBoot", "Spring Boot", "SSM", "Django", "Flask", "Node.js");
            if (backend == null && containsAny(corpus, "springboot", "spring boot", "spring")) {
                backend = "SpringBoot";
            }
            if (backend == null) {
                backend = "SpringBoot";
            }
            String frontend = firstMatch(corpus, "Vue", "React", "Angular", "小程序");
            if (frontend == null && containsAny(corpus, "vue")) {
                frontend = "Vue";
            }
            if (frontend == null) {
                frontend = "Vue";
            }
            String database = firstMatch(corpus, "MySQL", "PostgreSQL", "Oracle", "SQL Server", "MongoDB", "Redis");
            if (database == null) {
                boolean hasSql = session.getUserInputs() != null
                    && StringUtils.isNotBlank(session.getUserInputs().getSqlContent());
                database = hasSql ? "MySQL" : "MySQL";
            }
            return new TechStack(language, normalizeBackend(backend), frontend, database);
        }

        String resolveForTitle(String title, int introIndex) {
            String lower = title.toLowerCase(Locale.ROOT);
            if (lower.contains("数据库")) {
                return database;
            }
            if (lower.contains("框架")) {
                if (containsAny(lower, "前端", "vue", "react")) {
                    return frontend;
                }
                return backend;
            }
            if (lower.contains("简介")) {
                if (introIndex == 0) {
                    return language;
                }
                return frontend;
            }
            return backend;
        }

        private static String buildCorpus(PaperSession session) {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isNotBlank(session.getTitle())) {
                sb.append(session.getTitle()).append(' ');
            }
            if (session.getUserInputs() != null) {
                if (StringUtils.isNotBlank(session.getUserInputs().getEnvInfo())) {
                    sb.append(session.getUserInputs().getEnvInfo()).append(' ');
                }
                if (StringUtils.isNotBlank(session.getUserInputs().getCodeContent())) {
                    sb.append(session.getUserInputs().getCodeContent(), 0, Math.min(2000, session.getUserInputs().getCodeContent().length())).append(' ');
                }
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        }

        private static String firstMatch(String corpus, String... candidates) {
            for (String candidate : candidates) {
                if (containsNormalized(corpus, candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private static boolean containsAny(String text, String... tokens) {
            for (String token : tokens) {
                if (text.contains(token.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsNormalized(String corpus, String candidate) {
            String normalized = candidate.toLowerCase(Locale.ROOT).replace(" ", "");
            String compact = corpus.replace(" ", "");
            return compact.contains(normalized) || corpus.contains(candidate.toLowerCase(Locale.ROOT));
        }

        private static String normalizeBackend(String backend) {
            if ("Spring Boot".equalsIgnoreCase(backend)) {
                return "SpringBoot";
            }
            return backend;
        }
    }
}
