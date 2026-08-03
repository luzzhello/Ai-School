package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 启动按需文献抓取。
 */
@Data
public class PaperLitOnDemandStartRequest {

    @NotBlank(message = "会话 id 不能为空")
    private String sessionId;
}
