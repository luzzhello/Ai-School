package org.ruoyi.domain.entity.usercenter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("uc_redeem_code")
public class UcRedeemCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "code_id")
    private Long codeId;

    private String code;

    private Long coins;

    private Integer maxUses;

    private Integer usedCount;

    private Date expireTime;

    private String status;

    private String remark;

    private Date createTime;
}
