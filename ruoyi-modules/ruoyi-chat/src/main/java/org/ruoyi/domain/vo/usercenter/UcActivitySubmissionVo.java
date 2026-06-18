package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class UcActivitySubmissionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String username;

    private String nickName;

    private String activityType;

    private String feedbackType;

    private String subtype;

    private String relatedApps;

    private String contact;

    private String content;

    private List<String> images;

    private String remark;

    private String status;

    private Long rewardCoins;

    private String auditRemark;

    private Date auditTime;

    private Date createTime;
}
