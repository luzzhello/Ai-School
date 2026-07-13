package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.TocNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准计算机毕业论文目录模板（固定阿拉伯数字编号，不依赖 AI）。
 */
final class PaperStandardTocTemplate {

    private PaperStandardTocTemplate() {
    }

    static List<TocNode> build(List<String> tables) {
        List<TocNode> roots = new ArrayList<>();
        roots.add(node("abstract", "摘要", 1));
        roots.add(chapter1());
        roots.add(chapter2());
        roots.add(chapter3());
        roots.add(chapter4());
        roots.add(chapter5(tables, null));
        roots.add(chapter6());
        roots.add(node("references", "参考文献", 1));
        return roots;
    }

    private static TocNode chapter1() {
        return branch("ch1", "1 绪论", 1, List.of(
            node("ch1_1", "1.1 研究背景", 2),
            node("ch1_2", "1.2 研究意义", 2),
            branch("ch1_3", "1.3 国内外研究现状", 2, List.of(
                node("ch1_3_1", "1.3.1 国内研究现状", 3),
                node("ch1_3_2", "1.3.2 国外研究现状", 3)
            )),
            node("ch1_4", "1.4 研究内容", 2)
        ));
    }

    private static TocNode chapter2() {
        return branch("ch2", "2 相关技术", 1, List.of(
            node("ch2_1", "2.1 Java 语言", 2),
            node("ch2_2", "2.2 SpringBoot 框架", 2),
            node("ch2_3", "2.3 Vue 框架", 2),
            node("ch2_4", "2.4 MySQL 数据库", 2),
            node("ch2_5", "2.5 B/S 架构", 2)
        ));
    }

    private static TocNode chapter3() {
        return branch("ch3", "3 系统分析", 1, List.of(
            node("ch3_1", "3.1 需求分析", 2),
            branch("ch3_2", "3.2 可行性分析", 2, List.of(
                node("ch3_2_1", "3.2.1 技术可行性", 3),
                node("ch3_2_2", "3.2.2 经济可行性", 3),
                node("ch3_2_3", "3.2.3 操作可行性", 3)
            ))
        ));
    }

    private static TocNode chapter4() {
        return branch("ch4", "4 系统设计", 1, List.of(
            node("ch4_1", "4.1 系统功能设计", 2),
            node("ch4_2", "4.2 系统流程设计", 2),
            branch("ch4_3", "4.3 数据库设计", 2, List.of(
                node("ch4_3_1", "4.3.1 数据库 E-R 图设计", 3),
                node("ch4_3_2", "4.3.2 数据库表设计", 3)
            ))
        ));
    }

    private static TocNode chapter5(List<String> tables, PaperSession.SqlParsed sqlParsed) {
        List<TocNode> children = new ArrayList<>();
        if (tables != null && !tables.isEmpty()) {
            int index = 1;
            for (String table : tables) {
                String module = PaperTableLabelResolver.resolveEntityLabel(table, sqlParsed);
                if (PaperTableLabelResolver.isEnglishTableFragment(module)) {
                    module = PaperModuleDictionary.inferModuleName(table);
                    if (StringUtils.isNotBlank(module)) {
                        module = module.replace("管理", "").trim();
                    }
                }
                if (StringUtils.isBlank(module)) {
                    module = "业务";
                }
                children.add(node("ch5_" + index, "5." + index + " " + module + "模块实现", 2));
                index++;
            }
        } else {
            children.add(node("ch5_1", "5.1 系统核心模块实现", 2));
        }
        return branch("ch5", "5 系统实现", 1, children);
    }

    private static TocNode chapter6() {
        return branch("ch6", "6 系统测试", 1, List.of(
            node("ch6_1", "6.1 测试目的", 2),
            node("ch6_2", "6.2 测试环境", 2),
            node("ch6_3", "6.3 测试过程", 2),
            node("ch6_4", "6.4 测试结论", 2)
        ));
    }

    private static TocNode node(String id, String title, int level) {
        TocNode node = new TocNode();
        node.setId(id);
        node.setTitle(title);
        node.setLevel(level);
        node.setStatus("pending");
        node.setGenerated(false);
        node.setChildren(new ArrayList<>());
        return node;
    }

    private static TocNode branch(String id, String title, int level, List<TocNode> children) {
        TocNode node = node(id, title, level);
        node.setChildren(new ArrayList<>(children));
        return node;
    }
}
