package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 会员套餐 uc_membership_plan
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("uc_membership_plan")
public class UcMembershipPlan extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "plan_id")
    private Long planId;

    private String planCode;

    private String planName;

    private Long priceCoins;

    private Long originalCoins;

    private Integer durationDays;

    private Integer sortOrder;

    private String benefitsJson;

    private String status;

    private String remark;
}
