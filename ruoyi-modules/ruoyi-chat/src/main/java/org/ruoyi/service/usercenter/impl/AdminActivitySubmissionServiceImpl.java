package org.ruoyi.service.usercenter.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.usercenter.UcActivitySubmissionAuditBo;
import org.ruoyi.domain.bo.usercenter.UcActivitySubmissionBo;
import org.ruoyi.domain.dto.usercenter.UcActivityUserBrief;
import org.ruoyi.domain.entity.usercenter.UcActivitySubmission;
import org.ruoyi.domain.vo.usercenter.UcActivitySubmissionVo;
import org.ruoyi.mapper.usercenter.UcActivitySubmissionMapper;
import org.ruoyi.mapper.usercenter.UcActivityUserMapper;
import org.ruoyi.service.usercenter.IAdminActivitySubmissionService;
import org.ruoyi.service.usercenter.IUserWalletService;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AdminActivitySubmissionServiceImpl implements IAdminActivitySubmissionService {

    private final UcActivitySubmissionMapper submissionMapper;
    private final UcActivityUserMapper activityUserMapper;
    private final IUserWalletService walletService;

    @Override
    public TableDataInfo<UcActivitySubmissionVo> queryPageList(UcActivitySubmissionBo bo, PageQuery pageQuery) {
        return TenantHelper.ignore(() -> {
            LambdaQueryWrapper<UcActivitySubmission> lqw = buildQueryWrapper(bo);
            Page<UcActivitySubmission> page = submissionMapper.selectPage(pageQuery.build(), lqw);
            List<UcActivitySubmissionVo> rows = page.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());
            Page<UcActivitySubmissionVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            voPage.setRecords(rows);
            return TableDataInfo.build(voPage);
        });
    }

    @Override
    public UcActivitySubmissionVo queryById(Long id) {
        return TenantHelper.ignore(() -> {
            UcActivitySubmission row = submissionMapper.selectById(id);
            if (row == null) {
                throw new ServiceException("记录不存在");
            }
            return toVo(row);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(UcActivitySubmissionAuditBo bo) {
        TenantHelper.ignore(() -> {
            UcActivitySubmission row = submissionMapper.selectById(bo.getId());
            if (row == null) {
                throw new ServiceException("记录不存在");
            }
            if (!UserCenterConstants.SUBMISSION_PENDING.equals(row.getStatus())) {
                throw new ServiceException("该申请已审核，请勿重复操作");
            }

            if (UserCenterConstants.SUBMISSION_APPROVED.equals(bo.getStatus())) {
                long coins = bo.getRewardCoins() == null ? 0L : bo.getRewardCoins();
                if (coins < 0) {
                    throw new ServiceException("奖励金币不能为负数");
                }
                row.setStatus(UserCenterConstants.SUBMISSION_APPROVED);
                row.setRewardCoins(coins);
                if (coins > 0) {
                    walletService.changeBalance(
                        row.getUserId(),
                        coins,
                        UserCenterConstants.BIZ_ACTIVITY_REWARD,
                        "SUB" + row.getId(),
                        buildRewardDesc(row, coins));
                }
            }
            else if (UserCenterConstants.SUBMISSION_REJECTED.equals(bo.getStatus())) {
                row.setStatus(UserCenterConstants.SUBMISSION_REJECTED);
                row.setRewardCoins(0L);
            }
            else {
                throw new ServiceException("无效的审核状态");
            }

            row.setAuditRemark(bo.getAuditRemark());
            row.setAuditBy(LoginHelper.getUserId());
            row.setAuditTime(new Date());
            submissionMapper.updateById(row);
            return null;
        });
    }

    private String buildRewardDesc(UcActivitySubmission row, long coins) {
        String type = "SHARE".equals(row.getActivityType()) ? "分享申请" : "Bug反馈";
        return type + "审核通过，奖励 " + coins + " 金币";
    }

    private LambdaQueryWrapper<UcActivitySubmission> buildQueryWrapper(UcActivitySubmissionBo bo) {
        LambdaQueryWrapper<UcActivitySubmission> lqw = Wrappers.lambdaQuery();
        lqw.orderByDesc(UcActivitySubmission::getCreateTime);
        lqw.eq(bo.getId() != null, UcActivitySubmission::getId, bo.getId());
        lqw.eq(bo.getUserId() != null, UcActivitySubmission::getUserId, bo.getUserId());
        lqw.eq(StringUtils.isNotBlank(bo.getActivityType()), UcActivitySubmission::getActivityType, bo.getActivityType());
        lqw.eq(StringUtils.isNotBlank(bo.getFeedbackType()), UcActivitySubmission::getFeedbackType, bo.getFeedbackType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), UcActivitySubmission::getStatus, bo.getStatus());
        lqw.like(StringUtils.isNotBlank(bo.getContact()), UcActivitySubmission::getContact, bo.getContact());
        return lqw;
    }

    private UcActivitySubmissionVo toVo(UcActivitySubmission row) {
        UcActivitySubmissionVo vo = new UcActivitySubmissionVo();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setActivityType(row.getActivityType());
        vo.setFeedbackType(row.getFeedbackType());
        vo.setSubtype(row.getSubtype());
        vo.setRelatedApps(row.getRelatedApps());
        vo.setContact(row.getContact());
        vo.setContent(row.getContent());
        vo.setRemark(row.getRemark());
        vo.setStatus(row.getStatus());
        vo.setRewardCoins(row.getRewardCoins());
        vo.setAuditRemark(row.getAuditRemark());
        vo.setAuditTime(row.getAuditTime());
        vo.setCreateTime(row.getCreateTime());
        if (StringUtils.isNotBlank(row.getImagesJson())) {
            vo.setImages(parseImages(row.getImagesJson()));
        }
        if (row.getUserId() != null) {
            UcActivityUserBrief brief = activityUserMapper.selectUserBrief(row.getUserId());
            if (brief != null) {
                vo.setUsername(brief.getUsername());
                vo.setNickName(brief.getNickName());
            }
        }
        return vo;
    }

    private List<String> parseImages(String imagesJson) {
        try {
            if (imagesJson.startsWith("[")) {
                return JSONUtil.toList(imagesJson, String.class);
            }
            return List.of(imagesJson);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
