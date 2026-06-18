package org.ruoyi.mapper.usercenter;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import org.ruoyi.domain.dto.usercenter.UcActivityUserBrief;

@Mapper
public interface UcActivityUserMapper {

    @Select("""
        SELECT user_id FROM sys_user
        WHERE invite_code = #{inviteCode} AND del_flag = '0'
        LIMIT 1
        """)
    Long selectUserIdByInviteCode(@Param("inviteCode") String inviteCode);

    @Select("""
        SELECT user_id AS userId, user_name AS username, nick_name AS nickName, invite_code AS inviteCode
        FROM sys_user
        WHERE user_id = #{userId} AND del_flag = '0'
        LIMIT 1
        """)
    UcActivityUserBrief selectUserBrief(@Param("userId") Long userId);

    @Select("""
        SELECT user_id FROM sys_user
        WHERE del_flag = '0'
          AND (user_name LIKE CONCAT('%', #{keyword}, '%') OR nick_name LIKE CONCAT('%', #{keyword}, '%'))
        """)
    List<Long> selectUserIdsByKeyword(@Param("keyword") String keyword);
}
