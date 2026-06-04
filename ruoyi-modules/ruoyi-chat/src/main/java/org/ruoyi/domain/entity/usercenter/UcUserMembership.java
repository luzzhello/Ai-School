package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 用户会员 uc_user_membership
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("uc_user_membership")
public class UcUserMembership extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private String planCode;

    private String planName;

    private Date startTime;

    private Date expireTime;

    /** 0有效 1过期 */
    private String status;
}
