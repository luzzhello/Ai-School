package org.ruoyi.mapper.usercenter;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.entity.usercenter.UcInviteBind;

@Mapper
public interface UcInviteBindMapper extends BaseMapperPlus<UcInviteBind, UcInviteBind> {

    @Select("""
        SELECT COALESCE(SUM(coins_inviter), 0) FROM uc_invite_bind
        WHERE inviter_id = #{inviterId} AND month_key = #{monthKey}
        """)
    Long sumInviterCoinsByMonth(@Param("inviterId") Long inviterId, @Param("monthKey") String monthKey);
}
