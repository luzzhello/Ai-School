package org.ruoyi.service.usercenter;

import org.ruoyi.domain.dto.request.usercenter.ActivityInviteBindRequest;
import org.ruoyi.domain.dto.request.usercenter.ActivityRedeemRequest;
import org.ruoyi.domain.dto.request.usercenter.ActivitySubmissionRequest;
import org.ruoyi.domain.vo.usercenter.ActivityCheckInResultVo;
import org.ruoyi.domain.vo.usercenter.ActivityCheckInStatusVo;
import org.ruoyi.domain.vo.usercenter.ActivityInviteInfoVo;
import org.ruoyi.domain.vo.usercenter.ActivityRedeemResultVo;
import org.ruoyi.domain.vo.usercenter.UcActivitySubmissionUserVo;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

public interface IUserActivityService {

    ActivityCheckInStatusVo checkInStatus(Long userId, Integer year, Integer month);

    ActivityCheckInResultVo checkIn(Long userId);

    ActivityInviteInfoVo inviteInfo(Long userId, String inviteCode);

    void bindInvite(Long userId, ActivityInviteBindRequest request);

    Long submitFeedback(Long userId, ActivitySubmissionRequest request);

    ActivityRedeemResultVo redeem(Long userId, ActivityRedeemRequest request);

    String uploadImage(Long userId, MultipartFile file);

    TableDataInfo<UcActivitySubmissionUserVo> listMySubmissions(Long userId, String activityType, PageQuery pageQuery);

    UcActivitySubmissionUserVo getMySubmission(Long userId, Long id);
}
