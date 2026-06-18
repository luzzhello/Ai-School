package org.ruoyi.domain.vo.usercenter;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.domain.entity.usercenter.UcMembershipFeatureQuota;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@AutoMapper(target = UcMembershipFeatureQuota.class)
public class UcMembershipFeatureQuotaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long quotaId;

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

    private Date createTime;

    private Date updateTime;
}
