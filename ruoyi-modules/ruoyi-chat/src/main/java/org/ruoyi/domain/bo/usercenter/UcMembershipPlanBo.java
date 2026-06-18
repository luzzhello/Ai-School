package org.ruoyi.domain.bo.usercenter;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.domain.entity.usercenter.UcMembershipPlan;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = UcMembershipPlan.class, reverseConvertGenerate = false)
public class UcMembershipPlanBo extends BaseEntity {

    @NotNull(message = "主键不能为空", groups = { EditGroup.class })
    private Long planId;

    @NotBlank(message = "套餐编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String planCode;

    @NotBlank(message = "套餐名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String planName;

    @NotNull(message = "售价不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long priceCoins;

    private Long originalCoins;

    @NotNull(message = "有效天数不能为空", groups = { AddGroup.class, EditGroup.class })
    private Integer durationDays;

    private Integer sortOrder;

    private String benefitsJson;

    private String displayJson;

    private String status;

    private String remark;
}
