package org.ruoyi.domain.dto.response;

import lombok.Builder;
import lombok.Data;
import org.ruoyi.domain.paper.PaperSession.SqlParsed;
import org.ruoyi.domain.paper.TocNode;

import java.util.List;

/**
 * 论文 SQL 解析结果（含可能经 AI 优化后的最终 SQL）。
 */
@Data
@Builder
public class PaperParseSqlResponse {

    private SqlParsed parsed;

    /** 会话中保存的 SQL（可能已补全外键） */
    private String sqlContent;

    /** 是否经过 AI 优化 */
    private boolean sqlOptimized;

    /** 若会话已有大纲，解析 SQL 后自动刷新第五章并返回最新目录 */
    private List<TocNode> toc;

    /** 是否刷新了第五章大纲 */
    private boolean tocRefreshed;
}
