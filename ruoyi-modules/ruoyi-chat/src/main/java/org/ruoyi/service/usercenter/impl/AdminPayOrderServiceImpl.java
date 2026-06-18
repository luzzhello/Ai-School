package org.ruoyi.service.usercenter.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.usercenter.UcPayOrderBo;
import org.ruoyi.domain.entity.usercenter.UcWalletPayOrder;
import org.ruoyi.domain.vo.usercenter.UcPayOrderAdminVo;
import org.ruoyi.mapper.usercenter.UcWalletPayOrderMapper;
import org.ruoyi.service.usercenter.IAdminPayOrderService;
import org.ruoyi.service.usercenter.IPayOrderService;
import org.ruoyi.service.usercenter.UserCenterAdminHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AdminPayOrderServiceImpl implements IAdminPayOrderService {

    private final UcWalletPayOrderMapper payOrderMapper;
    private final IPayOrderService payOrderService;
    private final UserCenterAdminHelper adminHelper;

    @Override
    public TableDataInfo<UcPayOrderAdminVo> queryPageList(UcPayOrderBo bo, PageQuery pageQuery) {
        return TenantHelper.ignore(() -> {
            LambdaQueryWrapper<UcWalletPayOrder> lqw = buildQueryWrapper(bo);
            Page<UcWalletPayOrder> page = payOrderMapper.selectPage(pageQuery.build(), lqw);
            page.getRecords().forEach(payOrderService::refreshOrderExpire);
            List<UcPayOrderAdminVo> rows = page.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());
            Page<UcPayOrderAdminVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            voPage.setRecords(rows);
            return TableDataInfo.build(voPage);
        });
    }

    private LambdaQueryWrapper<UcWalletPayOrder> buildQueryWrapper(UcPayOrderBo bo) {
        LambdaQueryWrapper<UcWalletPayOrder> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getUserId() != null, UcWalletPayOrder::getUserId, bo.getUserId());
            lqw.like(StringUtils.isNotBlank(bo.getOrderNo()), UcWalletPayOrder::getOrderNo, bo.getOrderNo());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), UcWalletPayOrder::getStatus, bo.getStatus());
            lqw.eq(StringUtils.isNotBlank(bo.getOrderType()), UcWalletPayOrder::getOrderType, bo.getOrderType());
            lqw.like(StringUtils.isNotBlank(bo.getOrderName()), UcWalletPayOrder::getOrderName, bo.getOrderName());
            adminHelper.applyCreateTimeRange(lqw, UcWalletPayOrder::getCreateTime, bo);
        }
        lqw.orderByDesc(UcWalletPayOrder::getCreateTime);
        return lqw;
    }

    private UcPayOrderAdminVo toVo(UcWalletPayOrder order) {
        UcPayOrderAdminVo vo = BeanUtil.copyProperties(order, UcPayOrderAdminVo.class);
        vo.setUserName(adminHelper.resolveDisplayName(order.getUserId()));
        return vo;
    }
}
