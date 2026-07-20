package org.ruoyi.domain.entity.lit;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("lit_paper")
public class LitPaperEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String cnkiId;

    private String doi;

    private String title;

    private String authors;

    private String organs;

    private String abstractText;

    private String keywords;

    /** 知网外文中译题名（仅 lit_paper_en） */
    private String titleZh;

    /** 知网外文中译摘要（仅 lit_paper_en） */
    private String abstractZh;

    /** 知网外文中译关键词（仅 lit_paper_en） */
    private String keywordsZh;

    private String source;

    private Integer year;

    private String volume;

    private String issue;

    private String pages;

    private String publisher;

    private String publishPlace;

    private String translator;

    private String degree;

    private String degreePlace;

    private String patentCountry;

    private String patentKind;

    private String patentNo;

    private String standardCode;

    private String publishDate;

    private String docType;

    private Integer citeCount;

    private String litSource;

    private String citationGbt;

    private String detailUrl;

    private String titleHash;

    private String crawlKeyword;

    private String status;

    private Date crawledAt;

    private Date createTime;

    private Date updateTime;
}
