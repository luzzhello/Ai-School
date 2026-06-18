package org.ruoyi.mapper.usercenter;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.entity.usercenter.UcCheckInLog;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface UcCheckInLogMapper extends BaseMapperPlus<UcCheckInLog, UcCheckInLog> {

    @Select("""
        SELECT check_date FROM uc_check_in_log
        WHERE user_id = #{userId}
          AND check_date >= #{startDate}
          AND check_date <= #{endDate}
        ORDER BY check_date
        """)
    List<LocalDate> listDatesByUserAndRange(@Param("userId") Long userId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);
}
