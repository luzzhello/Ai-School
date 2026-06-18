package org.ruoyi.mapper.usercenter;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.entity.usercenter.UcWalletLog;
import org.ruoyi.domain.vo.usercenter.UcWalletLogVo;

@Mapper
public interface UcWalletLogMapper extends BaseMapperPlus<UcWalletLog, UcWalletLogVo> {

    @Select("SELECT COALESCE(SUM(change_amount), 0) FROM uc_wallet_log WHERE user_id = #{userId}")
    Long sumChangeAmountByUserId(@Param("userId") Long userId);

    @Select("SELECT MIN(create_time) FROM uc_wallet_log WHERE user_id = #{userId}")
    java.util.Date minCreateTimeByUserId(@Param("userId") Long userId);
}
