package org.ruoyi.domain.entity.paper;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("paper_reference")
public class PaperReferenceEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String sessionId;

    private Integer refIndex;

    private String author;

    private String title;

    private String source;

    private Integer year;

    private String doi;

    private String type;

    private String citation;

    private String language;

    private String chapter;

    private Date createTime;

    private Date updateTime;
}
