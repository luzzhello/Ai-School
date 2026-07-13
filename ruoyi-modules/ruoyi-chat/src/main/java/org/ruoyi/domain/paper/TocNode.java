package org.ruoyi.domain.paper;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 论文大纲目录树节点（支持一/二/三级框架）。
 * 对应 PRD「3.3 论文大纲生成模块」与左侧目录树导航。
 */
@Data
public class TocNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点 id，与 {@link PaperSession#getGeneratedContent()} 的 key 对应（如 abstract、ch1_1） */
    private String id;

    /** 标题文本（如「1.1 研究背景」） */
    private String title;

    /** 层级：1=一级，2=二级，3=三级 */
    private Integer level;

    /** 期望字数约束（可空） */
    private Integer wordLimit;

    /** 该节生成指令/特殊要求提示（可空，注入章节级 Prompt） */
    private String prompt;

    /** 生成状态：pending（待生成）/ generating（生成中）/ done（已生成） */
    private String status = "pending";

    /** 是否已生成内容 */
    private boolean generated;

    /** 子节点 */
    private List<TocNode> children = new ArrayList<>();
}
