package org.ruoyi.domain.bo.usercenter;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.domain.entity.usercenter.UcMembershipFeatureQuota;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = UcMembershipFeatureQuota.class, reverseConvertGenerate = false)
public class UcMembershipFeatureQuotaBo extends BaseEntity {

    @NotNull(message = "主键不能为空", groups = { EditGroup.class })
    private Long quotaId;

    @NotBlank(message = "功能名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String featureName;

    private String featureCode;

    private String freeText;

    private String weekText;

    private String monthText;

    private String yearText;

    private Integer weekLimit;

    private Integer monthLimit;

    private Integer yearLimit;

    private String isCategory;

    private Integer sortOrder;

    private String status;

    private String remark;
}
