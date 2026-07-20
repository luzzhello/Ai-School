package org.ruoyi.domain.paper;

import lombok.Data;

import java.io.Serializable;

/**
 * 参考文献条目。论文生成以「参考文献优先」为原则，文献确认后锁定用于正文写作。
 * 对应 PRD「3.2 参考文献获取模块」。
 */
@Data
public class Reference implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 序号（正文角标 [1]、[2]... 与参考文献页排序一致） */
    private Integer index;

    /** 作者 */
    private String author;

    /** 标题 */
    private String title;

    /** 来源（期刊名 / 学校 / 出版社等） */
    private String source;

    /** 发表年份 */
    private Integer year;

    /** DOI（可空） */
    private String doi;

    /** 文献类型：J=期刊 / D=学位论文 / M=专著 / C=论文集 / P=专利 / S=标准 */
    private String type;

    /** 卷号 */
    private String volume;

    /** 期号 */
    private String issue;

    /** 起-止页码，如 45-52 */
    private String pages;

    /** 出版者 */
    private String publisher;

    /** 出版地 */
    private String publishPlace;

    /** 译者 */
    private String translator;

    /** 学位类型（学位论文） */
    private String degree;

    /** 授予单位所在地 */
    private String degreePlace;

    /** 专利国名 */
    private String patentCountry;

    /** 专利文献种类 */
    private String patentKind;

    /** 专利号 */
    private String patentNo;

    /** 技术标准代号 */
    private String standardCode;

    /** 专利/标准出版日期 */
    private String publishDate;

    /** 完整引文（按 GB/T 7714 格式拼接），如：作者.标题[J].期刊名,年份.DOI */
    private String citation;

    /** 语言：zh（中文）/ en（英文） */
    private String language;

    /** 预计插入章节位置（如 第一章 / 1.1 研究背景） */
    private String chapter;

    /** 摘要（自定义录入或检索结果展示用，可空） */
    private String abstractText;

    /** 文献详情页链接（来自文献库主表 detail_url） */
    private String detailUrl;
}
