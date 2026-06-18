package org.ruoyi.domain.bo.usercenter;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.domain.entity.usercenter.UcFeaturePrice;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = UcFeaturePrice.class, reverseConvertGenerate = false)
public class UcFeaturePriceBo extends BaseEntity {

    @NotNull(message = "主键不能为空", groups = { EditGroup.class })
    private Long id;

    @NotBlank(message = "功能编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String featureCode;

    @NotBlank(message = "功能名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String featureName;

    @NotBlank(message = "分类不能为空", groups = { AddGroup.class, EditGroup.class })
    private String category;

    @NotBlank(message = "计价方式不能为空", groups = { AddGroup.class, EditGroup.class })
    private String priceType;

    @NotNull(message = "金币价格不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long priceCoins;

    private String status;

    private Integer sortOrder;

    private String remark;
}
