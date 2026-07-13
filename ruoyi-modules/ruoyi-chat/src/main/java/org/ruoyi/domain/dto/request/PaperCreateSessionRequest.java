package org.ruoyi.domain.dto.request;

import lombok.Data;

/**
 * 论文生成智能体——创建会话请求体。
 * 字段均可选：题目与基础输入可在创建时一并带入，SQL 仍通过 /parse-sql 单独提交。
 */
@Data
public class PaperCreateSessionRequest {

    /** 论文题目 */
    private String title;

    /** Controller/Service 代码内容（可选） */
    private String codeContent;

    /** 开发环境信息（编程语言、框架、数据库等） */
    private String envInfo;

    /** 字数要求，默认 15000 */
    private Integer wordCount;

    /** 学历层次：本科 / 专科 */
    private String educationLevel;
}
