package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("uc_check_in_log")
public class UcCheckInLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private LocalDate checkDate;

    private Long coins;

    private Integer streak;

    private Date createTime;
}
