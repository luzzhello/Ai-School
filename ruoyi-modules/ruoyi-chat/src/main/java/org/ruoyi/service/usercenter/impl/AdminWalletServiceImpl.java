package org.ruoyi.service.usercenter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.usercenter.UcWalletBo;
import org.ruoyi.domain.entity.usercenter.UcWallet;
import org.ruoyi.domain.vo.usercenter.UcWalletAdminVo;
import org.ruoyi.mapper.usercenter.UcActivityUserMapper;
import org.ruoyi.mapper.usercenter.UcWalletMapper;
import org.ruoyi.service.usercenter.IAdminWalletService;
import org.ruoyi.service.usercenter.UserCenterAdminHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AdminWalletServiceImpl implements IAdminWalletService {

    private final UcWalletMapper walletMapper;
    private final UcActivityUserMapper activityUserMapper;
    private final UserCenterAdminHelper adminHelper;

    @Override
    public TableDataInfo<UcWalletAdminVo> queryPageList(UcWalletBo bo, PageQuery pageQuery) {
        return TenantHelper.ignore(() -> {
            LambdaQueryWrapper<UcWallet> lqw = buildQueryWrapper(bo);
            Page<UcWallet> page = walletMapper.selectPage(pageQuery.build(), lqw);
            List<UcWalletAdminVo> rows = page.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());
            Page<UcWalletAdminVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            voPage.setRecords(rows);
            return TableDataInfo.build(voPage);
        });
    }

    private LambdaQueryWrapper<UcWallet> buildQueryWrapper(UcWalletBo bo) {
        LambdaQueryWrapper<UcWallet> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getUserId() != null, UcWallet::getUserId, bo.getUserId());
            if (StringUtils.isNotBlank(bo.getUserName())) {
                List<Long> userIds = activityUserMapper.selectUserIdsByKeyword(bo.getUserName().trim());
                if (userIds.isEmpty()) {
                    lqw.eq(UcWallet::getUserId, -1L);
                }
                else {
                    lqw.in(UcWallet::getUserId, userIds);
                }
            }
            adminHelper.applyCreateTimeRange(lqw, UcWallet::getUpdateTime, bo);
        }
        lqw.orderByDesc(UcWallet::getUpdateTime);
        return lqw;
    }

    private UcWalletAdminVo toVo(UcWallet wallet) {
        UcWalletAdminVo vo = new UcWalletAdminVo();
        vo.setWalletId(wallet.getWalletId());
        vo.setUserId(wallet.getUserId());
        vo.setUserName(adminHelper.resolveDisplayName(wallet.getUserId()));
        vo.setBalance(wallet.getBalance());
        vo.setFrozenBalance(wallet.getFrozenBalance());
        vo.setCreateTime(wallet.getCreateTime());
        vo.setUpdateTime(wallet.getUpdateTime());
        return vo;
    }
}
