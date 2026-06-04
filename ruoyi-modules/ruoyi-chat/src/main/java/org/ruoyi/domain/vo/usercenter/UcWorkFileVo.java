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

    private String thumbnail;

    private Long fileSize;

    private String storageType;

    private Date createTime;

    private Date updateTime;
}
