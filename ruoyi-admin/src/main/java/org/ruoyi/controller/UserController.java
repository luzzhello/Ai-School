package org.ruoyi.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.system.domain.vo.WeixinQrCodeVo;
import org.ruoyi.system.domain.vo.WxQrLoginUserVo;
import org.ruoyi.system.service.IWxQrLoginService;
import org.ruoyi.system.util.WeChatMpApiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 用户端公开接口（微信扫码登录）
 */
@Slf4j
@SaIgnore
@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {

    private final IWxQrLoginService wxQrLoginService;
    private final WeChatMpApiClient weChatMpApiClient;

    /**
     * 获取微信登录二维码
     */
    @GetMapping("/qrcode")
    public R<WeixinQrCodeVo> qrcode() {
        return R.ok(wxQrLoginService.createLoginQrCode());
    }

    /**
     * 轮询扫码登录状态
     */
    @GetMapping("/login/qrcode")
    public R<WxQrLoginUserVo> loginByQrcode(@RequestParam String ticket, @RequestParam String clientId) {
        WxQrLoginUserVo loginUser = wxQrLoginService.pollLoginResult(ticket, clientId);
        if (loginUser == null) {
            // 未扫码时返回 200 + 空 data，避免前端轮询反复弹出错误提示
            return R.ok();
        }
        return R.ok(loginUser);
    }

    /**
     * 微信公众平台服务器配置回调
     */
    @GetMapping("/wechat/callback")
    public String verifyWechatCallback(
        @RequestParam(value = "signature", required = false) String signature,
        @RequestParam(value = "timestamp", required = false) String timestamp,
        @RequestParam(value = "nonce", required = false) String nonce,
        @RequestParam(value = "echostr", required = false) String echostr) {
        if (weChatMpApiClient.checkSignature(signature, timestamp, nonce)) {
            return echostr;
        }
        return "invalid";
    }

    /**
     * 接收扫码/关注事件
     */
    @PostMapping(
        value = "/wechat/callback",
        produces = "text/plain;charset=UTF-8",
        consumes = {"text/xml", "application/xml", "*/*"}
    )
    public String receiveWechatEvent(HttpServletRequest request) {
        try {
            String xml = readRequestBody(request);
            if (StrUtil.isBlank(xml)) {
                return "success";
            }
            Map<String, Object> map = XmlUtil.xmlToMap(xml);
            String msgType = asText(map.get("MsgType"));
            if (!"event".equalsIgnoreCase(msgType)) {
                return "success";
            }
            String event = asText(map.get("Event"));
            String eventKey = asText(map.get("EventKey"));
            String openid = asText(map.get("FromUserName"));
            String scene = extractScene(event, eventKey);
            if (StrUtil.isNotBlank(scene)) {
                wxQrLoginService.handleScanEvent(scene, openid);
            }
        }
        catch (Exception e) {
            log.warn("处理微信回调失败: {}", e.getMessage());
        }
        return "success";
    }

    private String readRequestBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
        catch (Exception e) {
            log.warn("读取微信回调报文失败: {}", e.getMessage());
            return "";
        }
    }

    private String extractScene(String event, String eventKey) {
        if (StrUtil.isBlank(eventKey)) {
            return "";
        }
        if ("subscribe".equalsIgnoreCase(event) && eventKey.startsWith("qrscene_")) {
            return eventKey.substring("qrscene_".length());
        }
        if ("SCAN".equalsIgnoreCase(event)) {
            return eventKey;
        }
        return "";
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
