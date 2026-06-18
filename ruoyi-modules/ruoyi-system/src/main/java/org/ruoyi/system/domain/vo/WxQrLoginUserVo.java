package org.ruoyi.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 微信扫码登录成功返回（对齐前端 LoginUser）
 */
@Data
public class WxQrLoginUserVo {

    private Long userId;

    private String username;

    @JsonProperty("nickName")
    private String nickName;

    private String avatar;

    private String token;

    private String userType;

    private String openid;
}
