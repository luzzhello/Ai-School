package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * AI 功能定价 uc_feature_price
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("uc_feature_price")
public class UcFeaturePrice extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String featureCode;

    private String featureName;

    /** draw / document */
    private String category;

    /** FIXED / PER_THOUSAND */
    private String priceType;

    private Long priceCoins;

    /** 0正常 1停用 */
    private String status;

    private Integer sortOrder;

    private String remark;
}
