package org.ruoyi.common.chat.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务场景对话请求对象
 * <p>
 * 在通用对话参数基础上，增加业务类型字段，
 * 后端根据业务类型从 chat_prompt 表加载对应系统提示词。
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizChatRequest extends ChatRequest {

    /**
     * 业务类型，对应 chat_prompt.prompt_code
     */
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

}
