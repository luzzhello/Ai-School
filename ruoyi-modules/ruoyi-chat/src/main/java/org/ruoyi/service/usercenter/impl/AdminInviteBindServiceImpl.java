package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.usercenter.UcInviteBindBo;
import org.ruoyi.domain.dto.usercenter.UcActivityUserBrief;
import org.ruoyi.domain.entity.usercenter.UcInviteBind;
import org.ruoyi.domain.vo.usercenter.UcInviteBindVo;
import org.ruoyi.mapper.usercenter.UcActivityUserMapper;
import org.ruoyi.mapper.usercenter.UcInviteBindMapper;
import org.ruoyi.service.usercenter.IAdminInviteBindService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AdminInviteBindServiceImpl implements IAdminInviteBindService {

    private final UcInviteBindMapper inviteBindMapper;
    private final UcActivityUserMapper activityUserMapper;

    @Override
    public TableDataInfo<UcInviteBindVo> queryPageList(UcInviteBindBo bo, PageQuery pageQuery) {
        return TenantHelper.ignore(() -> {
            LambdaQueryWrapper<UcInviteBind> lqw = Wrappers.lambdaQuery();
            lqw.orderByDesc(UcInviteBind::getCreateTime);
            lqw.eq(bo.getInviterId() != null, UcInviteBind::getInviterId, bo.getInviterId());
            lqw.eq(bo.getInviteeId() != null, UcInviteBind::getInviteeId, bo.getInviteeId());
            lqw.eq(StringUtils.isNotBlank(bo.getInviteCode()), UcInviteBind::getInviteCode, bo.getInviteCode());
            lqw.eq(StringUtils.isNotBlank(bo.getMonthKey()), UcInviteBind::getMonthKey, bo.getMonthKey());

            Page<UcInviteBind> page = inviteBindMapper.selectPage(pageQuery.build(), lqw);
            List<UcInviteBindVo> rows = page.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());
            Page<UcInviteBindVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            voPage.setRecords(rows);
            return TableDataInfo.build(voPage);
        });
    }

    private UcInviteBindVo toVo(UcInviteBind row) {
        UcInviteBindVo vo = new UcInviteBindVo();
        vo.setId(row.getId());
        vo.setInviteeId(row.getInviteeId());
        vo.setInviterId(row.getInviterId());
        vo.setInviteCode(row.getInviteCode());
        vo.setCoinsInviter(row.getCoinsInviter());
        vo.setCoinsInvitee(row.getCoinsInvitee());
        vo.setMonthKey(row.getMonthKey());
        vo.setCreateTime(row.getCreateTime());
        vo.setInviteeName(resolveDisplayName(row.getInviteeId()));
        vo.setInviterName(resolveDisplayName(row.getInviterId()));
        return vo;
    }

    private String resolveDisplayName(Long userId) {
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
}
