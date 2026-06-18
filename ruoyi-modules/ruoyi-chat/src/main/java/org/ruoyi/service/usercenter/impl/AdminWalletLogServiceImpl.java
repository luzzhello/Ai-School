package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.usercenter.UcWalletLogBo;
import org.ruoyi.domain.entity.usercenter.UcWalletLog;
import org.ruoyi.domain.vo.usercenter.UcWalletLogAdminVo;
import org.ruoyi.mapper.usercenter.UcWalletLogMapper;
import org.ruoyi.service.usercenter.IAdminWalletLogService;
import org.ruoyi.service.usercenter.UserCenterAdminHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AdminWalletLogServiceImpl implements IAdminWalletLogService {

    private final UcWalletLogMapper walletLogMapper;
    private final UserCenterAdminHelper adminHelper;

    @Override
    public TableDataInfo<UcWalletLogAdminVo> queryPageList(UcWalletLogBo bo, PageQuery pageQuery) {
        return TenantHelper.ignore(() -> {
            LambdaQueryWrapper<UcWalletLog> lqw = buildQueryWrapper(bo);
            Page<UcWalletLog> page = walletLogMapper.selectPage(pageQuery.build(), lqw);
            List<UcWalletLogAdminVo> rows = page.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());
            Page<UcWalletLogAdminVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            voPage.setRecords(rows);
            return TableDataInfo.build(voPage);
        });
    }

    private LambdaQueryWrapper<UcWalletLog> buildQueryWrapper(UcWalletLogBo bo) {
        LambdaQueryWrapper<UcWalletLog> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getUserId() != null, UcWalletLog::getUserId, bo.getUserId());
            lqw.eq(StringUtils.isNotBlank(bo.getBizType()), UcWalletLog::getBizType, bo.getBizType());
            lqw.like(StringUtils.isNotBlank(bo.getBizNo()), UcWalletLog::getBizNo, bo.getBizNo());
            lqw.like(StringUtils.isNotBlank(bo.getDescription()), UcWalletLog::getDescription, bo.getDescription());
            adminHelper.applyCreateTimeRange(lqw, UcWalletLog::getCreateTime, bo);
        }
        lqw.orderByDesc(UcWalletLog::getCreateTime);
        return lqw;
    }

    private UcWalletLogAdminVo toVo(UcWalletLog log) {
        UcWalletLogAdminVo vo = new UcWalletLogAdminVo();
        vo.setLogId(log.getLogId());
        vo.setUserId(log.getUserId());
        vo.setUserName(adminHelper.resolveDisplayName(log.getUserId()));
        vo.setBizType(log.getBizType());
        vo.setBizNo(log.getBizNo());
        vo.setDescription(log.getDescription());
        vo.setChangeAmount(log.getChangeAmount());
        vo.setBalanceAfter(log.getBalanceAfter());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
