package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 会员功能费用与次数配额 uc_membership_feature_quota
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("uc_membership_feature_quota")
public class UcMembershipFeatureQuota extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "quota_id")
    private Long quotaId;

    private String featureName;

    private String featureCode;

    private String freeText;

    private String weekText;

    private String monthText;

    private String yearText;

    /** 周会员每日次数，-1 无限，null 无会员配额 */
    private Integer weekLimit;

    /** 月会员每日次数 */
    private Integer monthLimit;

    /** 年会员每日次数 */
    private Integer yearLimit;

    /** 0否 1是 */
    private String isCategory;

    private Integer sortOrder;

    private String status;

    private String remark;
}
