package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 论文排版模板启停请求。
 */
@Data
public class PaperFormatTemplateStatusRequest {

    /** 状态：0 停用 / 1 启用 */
    @NotBlank(message = "状态不能为空")
    private String status;
}
