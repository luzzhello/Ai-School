package org.ruoyi.domain.bo.usercenter;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
public class UcWorkFileBo extends BaseEntity {

    private Long userId;

    private String fileName;

    private String fileType;
}
