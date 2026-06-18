package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("uc_redeem_log")
public class UcRedeemLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long codeId;

    private String code;

    private Long coins;

    private Date createTime;
}
