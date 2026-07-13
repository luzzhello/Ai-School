package org.ruoyi.domain.entity.paper;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("paper_chapter")
public class PaperChapterEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String sessionId;

    private String chapterId;

    private String chapterTitle;

    /** pending / generating / done */
    private String status;

    private String content;

    private Date createTime;

    private Date updateTime;
}
