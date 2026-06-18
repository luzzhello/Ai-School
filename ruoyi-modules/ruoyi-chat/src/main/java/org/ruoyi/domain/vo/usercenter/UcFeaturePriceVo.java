package org.ruoyi.domain.vo.usercenter;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.domain.entity.usercenter.UcFeaturePrice;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@AutoMapper(target = UcFeaturePrice.class)
public class UcFeaturePriceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String featureCode;
    private String featureName;
    private String category;
    private String priceType;
    private Long priceCoins;
    private String status;
    private Integer sortOrder;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
