package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class UcWorkFileAdminVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long fileId;

    private Long userId;

    private String userName;

    private String fileName;

    private String description;

    private String fileType;

    private String subType;

    private String thumbnail;

    private Long fileSize;

    private String storageType;

    private Date createTime;

    private Date updateTime;
}
