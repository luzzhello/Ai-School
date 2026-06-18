package org.ruoyi.domain.bo.usercenter;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

/**
 * 活动提交查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UcActivitySubmissionBo extends BaseEntity {

    private Long id;

    private Long userId;

    private String activityType;

    private String feedbackType;

    private String status;

    private String contact;
}
