package org.ruoyi.service.usercenter.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.InviteCodeUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.dto.request.usercenter.ActivityInviteBindRequest;
import org.ruoyi.domain.dto.request.usercenter.ActivityRedeemRequest;
import org.ruoyi.domain.dto.request.usercenter.ActivitySubmissionRequest;
import org.ruoyi.domain.entity.usercenter.UcActivitySubmission;
import org.ruoyi.domain.entity.usercenter.UcCheckInLog;
import org.ruoyi.domain.entity.usercenter.UcInviteBind;
import org.ruoyi.domain.entity.usercenter.UcRedeemCode;
import org.ruoyi.domain.entity.usercenter.UcRedeemLog;
import org.ruoyi.domain.vo.usercenter.ActivityCheckInResultVo;
import org.ruoyi.domain.vo.usercenter.ActivityCheckInStatusVo;
import org.ruoyi.domain.vo.usercenter.ActivityInviteInfoVo;
import org.ruoyi.domain.vo.usercenter.ActivityRedeemResultVo;
import org.ruoyi.domain.vo.usercenter.UcActivitySubmissionUserVo;
import org.ruoyi.mapper.usercenter.UcActivitySubmissionMapper;
import org.ruoyi.mapper.usercenter.UcActivityUserMapper;
import org.ruoyi.mapper.usercenter.UcCheckInLogMapper;
import org.ruoyi.mapper.usercenter.UcInviteBindMapper;
import org.ruoyi.mapper.usercenter.UcRedeemCodeMapper;
import org.ruoyi.mapper.usercenter.UcRedeemLogMapper;
import org.ruoyi.service.usercenter.IUserActivityService;
import org.ruoyi.service.usercenter.IUserWalletService;
import org.ruoyi.service.usercenter.UserCenterConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.core.domain.dto.OssDTO;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class UserActivityServiceImpl implements IUserActivityService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UcCheckInLogMapper checkInLogMapper;
    private final UcInviteBindMapper inviteBindMapper;
    private final UcActivitySubmissionMapper submissionMapper;
    private final UcRedeemCodeMapper redeemCodeMapper;
    private final UcRedeemLogMapper redeemLogMapper;
    private final UcActivityUserMapper activityUserMapper;
    private final IUserWalletService walletService;
    private final OssService ossService;

    @Override
    public ActivityCheckInStatusVo checkInStatus(Long userId, Integer year, Integer month) {
        LocalDate today = LocalDate.now();
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();
        YearMonth ym = YearMonth.of(y, m);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        return TenantHelper.ignore(() -> {
            ActivityCheckInStatusVo vo = new ActivityCheckInStatusVo();
            vo.setYear(y);
            vo.setMonth(m);
            vo.setDailyReward(UserCenterConstants.CHECK_IN_DAILY_COINS);
            vo.setStreakBonusHint(UserCenterConstants.CHECK_IN_STREAK_BONUS);

            UcCheckInLog todayLog = findCheckIn(userId, today);
            vo.setCheckedToday(todayLog != null);
            vo.setStreak(todayLog != null ? todayLog.getStreak() : calcStreak(userId, today));
            vo.setTodayCoins(todayLog != null ? todayLog.getCoins() : UserCenterConstants.CHECK_IN_DAILY_COINS);

            List<LocalDate> dates = checkInLogMapper.listDatesByUserAndRange(userId, start, end);
            vo.setCheckedDays(dates.stream().map(LocalDate::getDayOfMonth).collect(Collectors.toList()));
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityCheckInResultVo checkIn(Long userId) {
        LocalDate today = LocalDate.now();
        return TenantHelper.ignore(() -> {
            if (findCheckIn(userId, today) != null) {
                throw new ServiceException("今日已签到");
            }
            int streak = calcStreak(userId, today) + 1;
            long coins = UserCenterConstants.CHECK_IN_DAILY_COINS;
            if (streak > 0 && streak % 7 == 0) {
                coins += UserCenterConstants.CHECK_IN_STREAK_BONUS;
            }

            UcCheckInLog log = new UcCheckInLog();
            log.setUserId(userId);
            log.setCheckDate(today);
            log.setCoins(coins);
            log.setStreak(streak);
            log.setCreateTime(new Date());
            checkInLogMapper.insert(log);

            String bizNo = "CHK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            walletService.changeBalance(userId, coins, UserCenterConstants.BIZ_CHECK_IN, bizNo,
                "每日签到，连续 " + streak + " 天，获得 " + coins + " 金币");

            ActivityCheckInResultVo vo = new ActivityCheckInResultVo();
            vo.setAddedCoins(coins);
            vo.setBalance(walletService.getBalance(userId));
            vo.setStreak(streak);
            return vo;
        });
    }

    @Override
    public ActivityInviteInfoVo inviteInfo(Long userId, String inviteCode) {
        String monthKey = LocalDate.now().format(MONTH_FMT);
        return TenantHelper.ignore(() -> {
            ActivityInviteInfoVo vo = new ActivityInviteInfoVo();
            vo.setInviteCode(StringUtils.isNotBlank(inviteCode)
                ? inviteCode
                : InviteCodeUtils.generateForUserId(userId));
            vo.setMonthlyCap(UserCenterConstants.INVITE_MONTHLY_CAP);
            vo.setRewardEach(UserCenterConstants.INVITE_REWARD_COINS);
            Long earned = inviteBindMapper.sumInviterCoinsByMonth(userId, monthKey);
            vo.setMonthlyEarned(earned == null ? 0L : earned);
            vo.setHasBound(inviteBindMapper.selectCount(
                Wrappers.<UcInviteBind>lambdaQuery().eq(UcInviteBind::getInviteeId, userId)) > 0);
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindInvite(Long userId, ActivityInviteBindRequest request) {
        String code = StringUtils.trim(request.getInviteCode()).toUpperCase();
        if (StringUtils.isBlank(code)) {
            throw new ServiceException("邀请码不能为空");
        }
        String myCode = InviteCodeUtils.generateForUserId(userId);
        if (code.equalsIgnoreCase(myCode)) {
            throw new ServiceException("不能填写自己的邀请码");
        }

        TenantHelper.ignore(() -> {
            long bound = inviteBindMapper.selectCount(
                Wrappers.<UcInviteBind>lambdaQuery().eq(UcInviteBind::getInviteeId, userId));
            if (bound > 0) {
                throw new ServiceException("您已绑定过邀请码");
            }

            Long inviterId = activityUserMapper.selectUserIdByInviteCode(code);
            if (inviterId == null) {
                throw new ServiceException("邀请码无效");
            }
            if (inviterId.equals(userId)) {
                throw new ServiceException("不能填写自己的邀请码");
            }

            String monthKey = LocalDate.now().format(MONTH_FMT);
            Long earned = inviteBindMapper.sumInviterCoinsByMonth(inviterId, monthKey);
            long inviterEarned = earned == null ? 0L : earned;
            long inviterReward = UserCenterConstants.INVITE_REWARD_COINS;
            if (inviterEarned + inviterReward > UserCenterConstants.INVITE_MONTHLY_CAP) {
                inviterReward = Math.max(0, UserCenterConstants.INVITE_MONTHLY_CAP - inviterEarned);
            }
            long inviteeReward = UserCenterConstants.INVITE_REWARD_COINS;

            UcInviteBind bind = new UcInviteBind();
            bind.setInviteeId(userId);
            bind.setInviterId(inviterId);
            bind.setInviteCode(code);
            bind.setCoinsInviter(inviterReward);
            bind.setCoinsInvitee(inviteeReward);
            bind.setMonthKey(monthKey);
            bind.setCreateTime(new Date());
            inviteBindMapper.insert(bind);

            String bizNo = "INV" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            if (inviterReward > 0) {
                walletService.changeBalance(inviterId, inviterReward, UserCenterConstants.BIZ_INVITE, bizNo + "A",
                    "邀请好友奖励 +" + inviterReward + " 金币");
            }
            walletService.changeBalance(userId, inviteeReward, UserCenterConstants.BIZ_INVITE_BIND, bizNo + "B",
                "填写邀请码奖励 +" + inviteeReward + " 金币");
            return null;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitFeedback(Long userId, ActivitySubmissionRequest request) {
        if (StringUtils.isBlank(request.getContent())) {
            throw new ServiceException("反馈内容不能为空");
        }
        return TenantHelper.ignore(() -> {
            UcActivitySubmission row = new UcActivitySubmission();
            row.setUserId(userId);
            row.setActivityType(StringUtils.upperCase(request.getActivityType()));
            row.setFeedbackType(request.getFeedbackType());
            row.setSubtype(request.getSubtype());
            if (request.getRelatedApps() != null && !request.getRelatedApps().isEmpty()) {
                row.setRelatedApps(String.join(",", request.getRelatedApps()));
            }
            row.setContact(request.getContact());
            row.setContent(request.getContent());
            if (request.getImages() != null && !request.getImages().isEmpty()) {
                row.setImagesJson(JSONUtil.toJsonStr(request.getImages()));
            }
            row.setRemark(request.getRemark());
            row.setStatus("0");
            row.setRewardCoins(0L);
            row.setCreateTime(new Date());
            submissionMapper.insert(row);
            return row.getId();
        });
    }

    @Override
    public String uploadImage(Long userId, MultipartFile file) {
        if (userId == null || userId <= 0) {
            throw new ServiceException("请先登录");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ServiceException("仅支持上传图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new ServiceException("单张图片不能超过 5MB");
        }
        OssDTO oss = ossService.uploadFile(file);
        if (oss == null || StringUtils.isBlank(oss.getUrl())) {
            throw new ServiceException("图片上传失败");
        }
        return oss.getUrl();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityRedeemResultVo redeem(Long userId, ActivityRedeemRequest request) {
        String code = StringUtils.trim(request.getCode()).toUpperCase();
        if (StringUtils.isBlank(code)) {
            throw new ServiceException("兑换码不能为空");
        }
        return TenantHelper.ignore(() -> {
            UcRedeemCode redeemCode = redeemCodeMapper.selectOne(
                Wrappers.<UcRedeemCode>lambdaQuery()
                    .eq(UcRedeemCode::getCode, code)
                    .eq(UcRedeemCode::getStatus, "0")
                    .last("LIMIT 1"));
            if (redeemCode == null) {
                throw new ServiceException("兑换码无效或已停用");
            }
            if (redeemCode.getExpireTime() != null && redeemCode.getExpireTime().before(new Date())) {
                throw new ServiceException("兑换码已过期");
            }
            if (redeemCode.getUsedCount() >= redeemCode.getMaxUses()) {
                throw new ServiceException("兑换码已达使用上限");
            }
            long used = redeemLogMapper.selectCount(
                Wrappers.<UcRedeemLog>lambdaQuery()
                    .eq(UcRedeemLog::getUserId, userId)
                    .eq(UcRedeemLog::getCodeId, redeemCode.getCodeId()));
            if (used > 0) {
                throw new ServiceException("您已使用过该兑换码");
            }

            UcRedeemLog log = new UcRedeemLog();
            log.setUserId(userId);
            log.setCodeId(redeemCode.getCodeId());
            log.setCode(redeemCode.getCode());
            log.setCoins(redeemCode.getCoins());
            log.setCreateTime(new Date());
            redeemLogMapper.insert(log);

            redeemCodeMapper.update(null, Wrappers.<UcRedeemCode>lambdaUpdate()
                .setSql("used_count = used_count + 1")
                .eq(UcRedeemCode::getCodeId, redeemCode.getCodeId()));

            String bizNo = "RDM" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            walletService.changeBalance(userId, redeemCode.getCoins(), UserCenterConstants.BIZ_REDEEM, bizNo,
                "兑换码 " + code + "，获得 " + redeemCode.getCoins() + " 金币");

            ActivityRedeemResultVo vo = new ActivityRedeemResultVo();
            vo.setAddedCoins(redeemCode.getCoins());
            vo.setBalance(walletService.getBalance(userId));
            return vo;
        });
    }

    @Override
    public TableDataInfo<UcActivitySubmissionUserVo> listMySubmissions(Long userId, String activityType, PageQuery pageQuery) {
        return TenantHelper.ignore(() -> {
            LambdaQueryWrapper<UcActivitySubmission> lqw = Wrappers.lambdaQuery();
            lqw.eq(UcActivitySubmission::getUserId, userId);
            lqw.eq(StringUtils.isNotBlank(activityType), UcActivitySubmission::getActivityType, activityType);
            lqw.orderByDesc(UcActivitySubmission::getCreateTime);
            Page<UcActivitySubmission> page = submissionMapper.selectPage(pageQuery.build(), lqw);
            List<UcActivitySubmissionUserVo> rows = page.getRecords().stream()
                .map(this::toUserVo)
                .collect(Collectors.toList());
            Page<UcActivitySubmissionUserVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            voPage.setRecords(rows);
            return TableDataInfo.build(voPage);
        });
    }

    @Override
    public UcActivitySubmissionUserVo getMySubmission(Long userId, Long id) {
        return TenantHelper.ignore(() -> {
            UcActivitySubmission row = submissionMapper.selectById(id);
            if (row == null || !userId.equals(row.getUserId())) {
                throw new ServiceException("记录不存在");
            }
            return toUserVo(row);
        });
    }

    private UcActivitySubmissionUserVo toUserVo(UcActivitySubmission row) {
        UcActivitySubmissionUserVo vo = new UcActivitySubmissionUserVo();
        vo.setId(row.getId());
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
            vo.setImages(JSONUtil.toList(row.getImagesJson(), String.class));
        }
        return vo;
    }

    private UcCheckInLog findCheckIn(Long userId, LocalDate date) {
        return checkInLogMapper.selectOne(
            Wrappers.<UcCheckInLog>lambdaQuery()
                .eq(UcCheckInLog::getUserId, userId)
                .eq(UcCheckInLog::getCheckDate, date)
                .last("LIMIT 1"));
    }

    private int calcStreak(Long userId, LocalDate today) {
        int streak = 0;
        LocalDate cursor = today.minusDays(1);
        while (findCheckIn(userId, cursor) != null) {
            streak++;
            cursor = cursor.minusDays(1);
            if (streak > 365) {
                break;
            }
        }
        return streak;
    }
}
