package org.ruoyi.domain.entity.paper;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("paper_format_template")
public class PaperFormatTemplateEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;
    private String name;
    private String schoolName;
    private Integer isDefault;
    private String status;
    private String docxPath;
    private String docxOriginalName;
    private Long docxSize;
    private String formatJson;
    private String styleMappingJson;
    private String remark;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
}
