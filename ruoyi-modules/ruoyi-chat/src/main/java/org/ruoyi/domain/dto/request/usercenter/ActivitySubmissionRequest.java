package org.ruoyi.domain.dto.request.usercenter;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ActivitySubmissionRequest {

    @NotBlank(message = "活动类型不能为空")
    private String activityType;

    private String feedbackType;

    private String subtype;

    private List<String> relatedApps;

    private String contact;

    @NotBlank(message = "反馈内容不能为空")
    private String content;

    private List<String> images;

    private String remark;
}
