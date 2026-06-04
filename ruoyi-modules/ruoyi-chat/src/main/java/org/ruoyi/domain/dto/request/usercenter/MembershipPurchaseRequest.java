package org.ruoyi.domain.dto.request.usercenter;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MembershipPurchaseRequest {

    @NotBlank(message = "套餐编码不能为空")
    private String planCode;
}
