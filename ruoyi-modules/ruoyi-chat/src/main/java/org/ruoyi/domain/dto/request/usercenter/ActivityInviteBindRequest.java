package org.ruoyi.domain.dto.request.usercenter;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivityInviteBindRequest {

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
