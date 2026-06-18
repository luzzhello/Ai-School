package org.ruoyi.domain.vo.usercenter;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.domain.entity.usercenter.UcMembershipPlan;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 会员套餐视图对象 uc_membership_plan
 */
@Data
@AutoMapper(target = UcMembershipPlan.class)
public class UcMembershipPlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long planId;

    private String planCode;

    private String planName;

    private Long priceCoins;

    private Long originalCoins;

    private Integer durationDays;

    private Integer sortOrder;

    private String benefitsJson;

    private String displayJson;

    private String status;

    private String remark;

    private Date createTime;

    private Date updateTime;

    /** 是否为当前用户套餐（非表字段） */
    private Boolean current;
}
