package org.ruoyi.system.service;

import cn.hutool.core.util.ReUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.constant.Constants;
import org.ruoyi.common.core.constant.GlobalConstants;
import org.ruoyi.common.core.constant.SystemConstants;
import org.ruoyi.common.core.domain.model.FrontRegisterBody;
import org.ruoyi.common.core.enums.UserType;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.exception.user.CaptchaException;
import org.ruoyi.common.core.exception.user.CaptchaExpireException;
import org.ruoyi.common.core.exception.user.UserException;
import org.ruoyi.common.core.utils.MessageUtils;
import org.ruoyi.common.core.utils.ServletUtils;
import org.ruoyi.common.core.utils.SpringUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.log.event.LogininforEvent;
import org.ruoyi.common.redis.utils.RedisUtils;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.system.domain.SysUser;
import org.ruoyi.system.domain.bo.SysUserBo;
import org.ruoyi.system.domain.vo.SysClientVo;
import org.ruoyi.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.ruoyi.common.core.constant.TenantConstants.DEFAULT_TENANT_ID;

/**
 * 前台用户注册（user_type = app_user）
 */
@RequiredArgsConstructor
@Service
public class FrontRegisterService {

    private static final String TYPE_EMAIL = "email";
    private static final String TYPE_SMS = "sms";

    private final ISysUserService userService;
    private final ISysClientService clientService;
    private final SysUserMapper userMapper;
    private final AppUserRoleBinder appUserRoleBinder;

    @Transactional(rollbackFor = Exception.class)
    public void register(FrontRegisterBody body) {
        String tenantId = StringUtils.blankToDefault(body.getTenantId(), DEFAULT_TENANT_ID);
        validateClient(body.getClientId());

        String registerType = StringUtils.trim(body.getRegisterType()).toLowerCase();
        SysUserBo sysUser = new SysUserBo();
        sysUser.setUserType(UserType.APP_USER.getUserType());
        sysUser.setStatus(SystemConstants.NORMAL);
        sysUser.setPassword(BCrypt.hashpw(body.getPassword()));

        if (TYPE_EMAIL.equals(registerType)) {
            fillEmailRegister(sysUser, body, tenantId);
        } else if (TYPE_SMS.equals(registerType)) {
            fillSmsRegister(sysUser, body, tenantId);
        } else {
            throw new ServiceException("不支持的注册方式");
        }

        boolean regFlag = userService.registerUser(sysUser, tenantId);
        if (!regFlag) {
            throw new UserException("user.register.error");
        }
        appUserRoleBinder.bindIfAbsent(sysUser.getUserId());
        recordLogininfor(tenantId, sysUser.getUserName(), Constants.REGISTER, MessageUtils.message("user.register.success"));
    }

    private void validateClient(String clientId) {
        SysClientVo client = clientService.queryByClientId(clientId);
        if (client == null || !SystemConstants.NORMAL.equals(client.getStatus())) {
            throw new ServiceException(MessageUtils.message("auth.grant.type.error"));
        }
    }

    private void fillEmailRegister(SysUserBo sysUser, FrontRegisterBody body, String tenantId) {
        String email = StringUtils.trim(body.getEmail());
        if (StringUtils.isBlank(email) || !ReUtil.isMatch("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$", email)) {
            throw new ServiceException("请输入正确的邮箱");
        }
        validateAndConsumeCode(GlobalConstants.CAPTCHA_CODE_KEY + email, body.getCode(), tenantId, email);

        if (existsByEmail(tenantId, email)) {
            throw new UserException("user.register.save.error", email);
        }

        sysUser.setEmail(email);
        sysUser.setUserName(email);
        sysUser.setNickName(resolveNickName(email.substring(0, email.indexOf('@'))));
        assertUnique(tenantId, sysUser);
    }

    private void fillSmsRegister(SysUserBo sysUser, FrontRegisterBody body, String tenantId) {
        String phonenumber = StringUtils.trim(body.getPhonenumber());
        if (StringUtils.isBlank(phonenumber) || !ReUtil.isMatch("^1[3-9]\\d{9}$", phonenumber)) {
            throw new ServiceException("请输入正确的手机号");
        }
        validateAndConsumeCode(GlobalConstants.CAPTCHA_CODE_KEY + phonenumber, body.getCode(), tenantId, phonenumber);

        if (existsByPhone(tenantId, phonenumber)) {
            throw new UserException("user.register.save.error", phonenumber);
        }

        sysUser.setPhonenumber(phonenumber);
        sysUser.setUserName(phonenumber);
        sysUser.setNickName("用户" + phonenumber.substring(phonenumber.length() - 4));
        assertUnique(tenantId, sysUser);
    }

    private void assertUnique(String tenantId, SysUserBo sysUser) {
        boolean exist = TenantHelper.dynamic(tenantId, () ->
            userMapper.exists(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserName, sysUser.getUserName())));
        if (exist) {
            throw new UserException("user.register.save.error", sysUser.getUserName());
        }
        SysUserBo check = new SysUserBo();
        check.setUserId(null);
        if (StringUtils.isNotBlank(sysUser.getEmail())) {
            check.setEmail(sysUser.getEmail());
            if (!userService.checkEmailUnique(check)) {
                throw new UserException("user.register.save.error", sysUser.getEmail());
            }
        }
        if (StringUtils.isNotBlank(sysUser.getPhonenumber())) {
            check.setEmail(null);
            check.setPhonenumber(sysUser.getPhonenumber());
            if (!userService.checkPhoneUnique(check)) {
                throw new UserException("user.register.save.error", sysUser.getPhonenumber());
            }
        }
    }

    private boolean existsByEmail(String tenantId, String email) {
        return TenantHelper.dynamic(tenantId, () ->
            userMapper.exists(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email)));
    }

    private boolean existsByPhone(String tenantId, String phonenumber) {
        return TenantHelper.dynamic(tenantId, () ->
            userMapper.exists(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhonenumber, phonenumber)));
    }

    private String resolveNickName(String base) {
        String nick = StringUtils.trim(base);
        if (StringUtils.isBlank(nick)) {
            return "新用户";
        }
        return nick.length() > 20 ? nick.substring(0, 20) : nick;
    }

    private void validateAndConsumeCode(String redisKey, String code, String tenantId, String account) {
        String cached = RedisUtils.getCacheObject(redisKey);
        if (StringUtils.isBlank(cached)) {
            recordLogininfor(tenantId, account, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        if (!StringUtils.equals(cached, code)) {
            recordLogininfor(tenantId, account, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error"));
            throw new CaptchaException();
        }
        RedisUtils.deleteObject(redisKey);
    }

    private void recordLogininfor(String tenantId, String username, String status, String message) {
        LogininforEvent logininforEvent = new LogininforEvent();
        logininforEvent.setTenantId(tenantId);
        logininforEvent.setUsername(username);
        logininforEvent.setStatus(status);
        logininforEvent.setMessage(message);
        logininforEvent.setRequest(ServletUtils.getRequest());
        SpringUtils.context().publishEvent(logininforEvent);
    }
}
