package org.ruoyi.service.usercenter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.domain.dto.usercenter.UcActivityUserBrief;
import org.ruoyi.mapper.usercenter.UcActivityUserMapper;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserCenterAdminHelper {

    private final UcActivityUserMapper activityUserMapper;

    public String resolveDisplayName(Long userId) {
        if (userId == null) {
            return "";
        }
        UcActivityUserBrief brief = activityUserMapper.selectUserBrief(userId);
        if (brief == null) {
            return String.valueOf(userId);
        }
        if (StringUtils.isNotBlank(brief.getNickName())) {
            return brief.getNickName();
        }
        return brief.getUsername();
    }

    public <T> void applyCreateTimeRange(LambdaQueryWrapper<T> lqw, SFunction<T, Date> column, BaseEntity bo) {
        Map<String, Object> params = bo.getParams();
        lqw.between(params.get("beginTime") != null && params.get("endTime") != null,
            column, params.get("beginTime"), params.get("endTime"));
    }
}
