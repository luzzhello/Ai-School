package org.ruoyi.mapper.usercenter;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.entity.usercenter.UcFeatureDailyUsage;

import java.time.LocalDate;

@Mapper
public interface UcFeatureDailyUsageMapper extends BaseMapperPlus<UcFeatureDailyUsage, UcFeatureDailyUsage> {

    @Select("""
        SELECT use_count FROM uc_feature_daily_usage
        WHERE user_id = #{userId}
          AND feature_code = #{featureCode}
          AND usage_date = #{usageDate}
        LIMIT 1
        """)
    Integer selectUseCount(@Param("userId") Long userId,
                           @Param("featureCode") String featureCode,
                           @Param("usageDate") LocalDate usageDate);
}
