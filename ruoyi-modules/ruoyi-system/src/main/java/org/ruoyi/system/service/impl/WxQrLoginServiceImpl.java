package org.ruoyi.system.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.constant.GlobalConstants;
import org.ruoyi.common.core.domain.dto.VisitorLoginUserDto;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.UserLoginService;
import org.ruoyi.common.redis.utils.RedisUtils;
import org.ruoyi.system.config.properties.WxMpProperties;
import org.ruoyi.system.domain.dto.WxQrTicketState;
import org.ruoyi.system.domain.vo.SysUserVo;
import org.ruoyi.system.domain.vo.WeixinQrCodeVo;
import org.ruoyi.system.domain.vo.WxQrLoginUserVo;
import org.ruoyi.system.service.ISysUserService;
import org.ruoyi.system.service.IWxQrLoginService;
import org.ruoyi.system.util.WeChatMpApiClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 微信公众号扫码登录
 */
@Service
@RequiredArgsConstructor
public class WxQrLoginServiceImpl implements IWxQrLoginService {

    private static final String STATUS_WAITING = "waiting";
    private static final String STATUS_SCANNED = "scanned";
    private static final String STATUS_LOGGED = "logged";

    private final WxMpProperties wxMpProperties;
    private final WeChatMpApiClient weChatMpApiClient;
    private final UserLoginService userLoginService;
    private final ISysUserService userService;

    @Override
    public WeixinQrCodeVo createLoginQrCode() {
        ensureEnabled();
        String scene = IdUtil.fastSimpleUUID();
        WeChatMpApiClient.QrCodeCreateResult result = weChatMpApiClient.createTempQrCode(scene);

        WxQrTicketState state = new WxQrTicketState();
        state.setScene(scene);
        state.setStatus(STATUS_WAITING);
        RedisUtils.setCacheObject(
            ticketKey(scene),
            state,
            Duration.ofSeconds(wxMpProperties.getExpireSeconds())
        );

        WeixinQrCodeVo vo = new WeixinQrCodeVo();
        vo.setTicket(scene);
        vo.setQrCodeUrl(result.qrCodeUrl());
        return vo;
    }

    @Override
    public WxQrLoginUserVo pollLoginResult(String ticket, String clientId) {
        if (StrUtil.isBlank(ticket)) {
            throw new ServiceException("ticket 不能为空");
        }
        if (StrUtil.isBlank(clientId)) {
            throw new ServiceException("clientId 不能为空");
        }

        WxQrTicketState state = RedisUtils.getCacheObject(ticketKey(ticket));
        if (state == null) {
            return null;
        }
        if (!STATUS_SCANNED.equals(state.getStatus()) || StrUtil.isBlank(state.getOpenid())) {
            return null;
        }
        if (STATUS_LOGGED.equals(state.getStatus())) {
            return null;
        }

        VisitorLoginUserDto loginUser = userLoginService.mpLogin(state.getOpenid(), clientId);
        state.setStatus(STATUS_LOGGED);
        RedisUtils.setCacheObject(ticketKey(ticket), state, Duration.ofMinutes(2));

        SysUserVo user = userService.selectUserByOpenId(state.getOpenid());
        return toLoginVo(loginUser, user);
    }

    @Override
    public void handleScanEvent(String scene, String openid) {
        if (StrUtil.hasBlank(scene, openid)) {
            return;
        }
        WxQrTicketState state = RedisUtils.getCacheObject(ticketKey(scene));
        if (state == null) {
            return;
        }
        state.setOpenid(openid);
        state.setStatus(STATUS_SCANNED);
        long ttl = RedisUtils.getTimeToLive(ticketKey(scene));
        Duration duration = ttl > 0 ? Duration.ofMillis(ttl) : Duration.ofSeconds(wxMpProperties.getExpireSeconds());
        RedisUtils.setCacheObject(ticketKey(scene), state, duration);
    }

    private WxQrLoginUserVo toLoginVo(VisitorLoginUserDto loginUser, SysUserVo user) {
        WxQrLoginUserVo vo = new WxQrLoginUserVo();
        vo.setUserId(loginUser.getUserId());
        vo.setUsername(loginUser.getUsername());
        vo.setToken(loginUser.getToken());
        vo.setUserType(loginUser.getUserType());
        vo.setOpenid(loginUser.getOpenid());
        if (user != null) {
            vo.setNickName(StrUtil.blankToDefault(user.getNickName(), user.getUserName()));
        }
        else {
            vo.setNickName(loginUser.getNickname());
        }
        return vo;
    }

    private void ensureEnabled() {
        if (!wxMpProperties.isEnabled()) {
            throw new ServiceException("微信扫码登录未启用");
        }
        weChatMpApiClient.resolveAppId();
        weChatMpApiClient.resolveAppSecret();
        if (StrUtil.isBlank(wxMpProperties.getToken())) {
            throw new ServiceException("请先配置微信公众平台服务器 Token（wx.mp.token）");
        }
    }

    private String ticketKey(String scene) {
        return GlobalConstants.WX_QRCODE_TICKET_KEY + scene;
    }
}
