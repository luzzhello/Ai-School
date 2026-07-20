package org.ruoyi.domain.vo.usercenter;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.domain.entity.usercenter.UcWorkFile;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 云端作品视图对象 uc_work_file
 */
@Data
@AutoMapper(target = UcWorkFile.class)
public class UcWorkFileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long fileId;

    private String fileName;

    private String description;

    private String fileType;

    /**
     * 软件工程图子类型（列表用摘要，来自 contentJson.diagramType；其它类型为空）
     */
    private String diagramType;

    private String thumbnail;

    private Long fileSize;

    private String storageType;

    private Date createTime;

    private Date updateTime;
}
