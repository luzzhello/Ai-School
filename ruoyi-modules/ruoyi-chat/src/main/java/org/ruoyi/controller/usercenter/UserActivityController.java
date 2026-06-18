package org.ruoyi.controller.usercenter;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.domain.model.LoginUser;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.dto.request.usercenter.ActivityInviteBindRequest;
import org.ruoyi.domain.dto.request.usercenter.ActivityRedeemRequest;
import org.ruoyi.domain.dto.request.usercenter.ActivitySubmissionRequest;
import org.ruoyi.domain.vo.usercenter.ActivityCheckInResultVo;
import org.ruoyi.domain.vo.usercenter.ActivityCheckInStatusVo;
import org.ruoyi.domain.vo.usercenter.ActivityInviteInfoVo;
import org.ruoyi.domain.vo.usercenter.ActivityRedeemResultVo;
import org.ruoyi.domain.vo.usercenter.UcActivitySubmissionUserVo;
import org.ruoyi.service.usercenter.IUserActivityService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 活动中心
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/usercenter/activity")
public class UserActivityController extends BaseController {

    private final IUserActivityService activityService;

    @GetMapping("/check-in/status")
    public R<ActivityCheckInStatusVo> checkInStatus(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer month) {
        Long userId = requireLogin().getUserId();
        return R.ok(activityService.checkInStatus(userId, year, month));
    }

    @PostMapping("/check-in")
    public R<ActivityCheckInResultVo> checkIn() {
        Long userId = requireLogin().getUserId();
        return R.ok(activityService.checkIn(userId));
    }

    @GetMapping("/invite/info")
    public R<ActivityInviteInfoVo> inviteInfo() {
        LoginUser user = requireLogin();
        return R.ok(activityService.inviteInfo(user.getUserId(), user.getInviteCode()));
    }

    @PostMapping("/invite/bind")
    public R<Void> bindInvite(@RequestBody @Valid ActivityInviteBindRequest request) {
        Long userId = requireLogin().getUserId();
        activityService.bindInvite(userId, request);
        return R.ok();
    }

    @PostMapping("/submit")
    public R<Map<String, Long>> submit(@RequestBody @Valid ActivitySubmissionRequest request) {
        Long userId = requireLogin().getUserId();
        Long id = activityService.submitFeedback(userId, request);
        Map<String, Long> data = new HashMap<>(1);
        data.put("submissionId", id);
        return R.ok(data);
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, String>> uploadImage(@RequestPart("file") MultipartFile file) {
        Long userId = requireLogin().getUserId();
        String url = activityService.uploadImage(userId, file);
        Map<String, String> data = new HashMap<>(1);
        data.put("url", url);
        return R.ok(data);
    }

    @PostMapping("/redeem")
    public R<ActivityRedeemResultVo> redeem(@RequestBody @Valid ActivityRedeemRequest request) {
        Long userId = requireLogin().getUserId();
        return R.ok(activityService.redeem(userId, request));
    }

    @GetMapping("/submissions")
    public TableDataInfo<UcActivitySubmissionUserVo> mySubmissions(
        @RequestParam(required = false) String activityType,
        PageQuery pageQuery) {
        Long userId = requireLogin().getUserId();
        return activityService.listMySubmissions(userId, activityType, pageQuery);
    }

    @GetMapping("/submissions/{id}")
    public R<UcActivitySubmissionUserVo> mySubmissionDetail(@PathVariable Long id) {
        Long userId = requireLogin().getUserId();
        return R.ok(activityService.getMySubmission(userId, id));
    }

    private LoginUser requireLogin() {
        if (!LoginHelper.isLogin()) {
            throw new ServiceException("请先登录");
        }
        return LoginHelper.getLoginUser();
    }
}
