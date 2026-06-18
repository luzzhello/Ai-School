package org.ruoyi.domain.dto.request.usercenter;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivityRedeemRequest {

    @NotBlank(message = "兑换码不能为空")
    private String code;
}
