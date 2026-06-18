package org.ruoyi.domain.dto.usercenter;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UcActivityUserBrief implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String nickName;

    private String inviteCode;
}
