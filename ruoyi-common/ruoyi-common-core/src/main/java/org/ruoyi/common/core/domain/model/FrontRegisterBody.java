package org.ruoyi.common.core.domain.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 前台用户注册（邮箱 / 手机号）
 */
@Data
public class FrontRegisterBody implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{auth.clientid.not.blank}")
    private String clientId;

    private String tenantId;

    /**
     * 注册方式：email | sms
     */
    @NotBlank(message = "注册方式不能为空")
    private String registerType;

    private String email;

    private String phonenumber;

    @NotBlank(message = "{user.password.not.blank}")
    @Length(min = 5, max = 30, message = "{user.password.length.valid}")
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
