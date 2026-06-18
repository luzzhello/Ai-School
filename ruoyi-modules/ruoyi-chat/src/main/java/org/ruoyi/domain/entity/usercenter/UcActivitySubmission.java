package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("uc_activity_submission")
public class UcActivitySubmission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private String activityType;

    private String feedbackType;

    private String subtype;

    private String relatedApps;

    private String contact;

    private String content;

    private String imagesJson;

    private String remark;

    private String status;

    private Long rewardCoins;

    private String auditRemark;

    private Long auditBy;

    private Date auditTime;

    private Date createTime;
}
