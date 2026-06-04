package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 云端作品 uc_work_file
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("uc_work_file")
public class UcWorkFile extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "file_id")
    private Long fileId;

    private Long userId;

    private String fileName;

    private String description;

    private String fileType;

    private String thumbnail;

    private String contentJson;

    private Long fileSize;

    private String storageType;

    private String tenantId;

    @TableLogic
    private String delFlag;
}
