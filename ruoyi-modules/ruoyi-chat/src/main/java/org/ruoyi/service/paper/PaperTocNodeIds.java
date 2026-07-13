package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.paper.TocNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 为论文大纲树分配稳定节点 id（abstract / ch1_1 / ch5_2_3 等）。
 */
final class PaperTocNodeIds {

    private PaperTocNodeIds() {
    }

    static void assign(List<TocNode> nodes, String parentPrefix) {
        if (nodes == null) {
            return;
        }
        int index = 1;
        for (TocNode node : nodes) {
            String special = specialId(node.getTitle());
            if (special != null) {
                node.setId(special);
            } else if (StringUtils.isBlank(parentPrefix)) {
                node.setId("ch" + index);
            } else {
                node.setId(parentPrefix + "_" + index);
            }
            index++;
            assign(node.getChildren(), node.getId());
        }
    }

    private static String specialId(String title) {
        if (StringUtils.isBlank(title)) {
            return null;
        }
        if (title.contains("摘要") && !title.toLowerCase().contains("abstract")) {
            return "abstract";
        }
        if (title.contains("参考文献")) {
            return "references";
        }
        if (title.contains("致谢")) {
            return "acknowledgement";
        }
        return null;
    }
}
