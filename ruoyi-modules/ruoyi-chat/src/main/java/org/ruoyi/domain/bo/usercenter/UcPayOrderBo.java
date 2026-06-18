package org.ruoyi.domain.bo.usercenter;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
public class UcPayOrderBo extends BaseEntity {

    private Long userId;

    private String orderNo;

    private String status;

    private String orderType;

    private String orderName;
}
