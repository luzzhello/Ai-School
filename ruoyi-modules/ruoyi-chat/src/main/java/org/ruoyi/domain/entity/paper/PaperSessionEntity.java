package org.ruoyi.domain.entity.paper;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("paper_session")
public class PaperSessionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String sessionId;

    private Long userId;

    private String title;

    /** init / ref_confirmed / toc_confirmed / writing / done */
    private String status;

    private Integer wordCount;

    private String educationLevel;

    private String envInfo;

    private String sqlContent;

    private String codeContent;

    private String sqlParsedJson;

    private String tocJson;

    /** 系统实现截图清单 JSON */
    private String uiScreenshotsJson;

    private Long formatTemplateId;

    private String formatOverrideJson;

    private String customFormatDocxPath;

    private String customFormatDocxName;

    private Long customFormatDocxSize;

    private String customFormatJson;

    private Integer customPatchStyles;

    private Date createTime;

    private Date updateTime;
}
