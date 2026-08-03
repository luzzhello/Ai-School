package org.ruoyi.domain.entity.draw;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("aigc_detect_report")
public class AigcDetectReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String reportId;

    private Long userId;

    private String title;

    private Integer wordCount;

    private BigDecimal aigcRate;

    private BigDecimal humanRate;

    private Integer costCoins;

    private String summary;

    private String inputMode;

    private String resultJson;

    private Date createTime;

    private Date updateTime;
}
