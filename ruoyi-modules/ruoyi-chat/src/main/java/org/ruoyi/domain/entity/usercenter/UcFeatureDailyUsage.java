package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("uc_feature_daily_usage")
public class UcFeatureDailyUsage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private String featureCode;

    private LocalDate usageDate;

    private Integer useCount;

    private Date createTime;

    private Date updateTime;
}
