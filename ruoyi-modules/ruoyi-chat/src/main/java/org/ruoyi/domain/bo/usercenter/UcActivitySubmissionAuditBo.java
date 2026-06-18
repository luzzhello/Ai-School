package org.ruoyi.domain.bo.usercenter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 活动提交审核
 */
@Data
public class UcActivitySubmissionAuditBo {

    @NotNull(message = "主键不能为空")
    private Long id;

    /** 1通过 2拒绝 */
    @NotBlank(message = "审核状态不能为空")
    private String status;

    private Long rewardCoins;

    private String auditRemark;
}
