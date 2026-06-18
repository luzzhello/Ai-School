package org.ruoyi.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.constant.SystemConstants;
import org.ruoyi.common.mybatis.helper.DataPermissionHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.system.domain.SysRole;
import org.ruoyi.system.domain.SysUserRole;
import org.ruoyi.system.mapper.SysRoleMapper;
import org.ruoyi.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;

import static org.ruoyi.common.core.constant.TenantConstants.DEFAULT_TENANT_ID;

/**
 * 为前台用户（app_user）绑定默认角色
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppUserRoleBinder {

    /** 与 updat-app-user-role.sql 中 sys_role.role_key 保持一致 */
    public static final String APP_USER_ROLE_KEY = "app_user";

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    /**
     * 若用户尚未绑定前台角色，则自动绑定（幂等）
     */
    public void bindIfAbsent(Long userId) {
        if (userId == null) {
            return;
        }
        DataPermissionHelper.ignore(() -> TenantHelper.dynamic(DEFAULT_TENANT_ID, () -> {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, APP_USER_ROLE_KEY)
                .eq(SysRole::getStatus, SystemConstants.NORMAL)
                .last("LIMIT 1"));
            if (role == null) {
                log.warn("前台默认角色 {} 未配置，请执行 updat-app-user-role.sql", APP_USER_ROLE_KEY);
                return;
            }
            boolean exists = userRoleMapper.exists(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, role.getRoleId()));
            if (exists) {
                return;
            }
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getRoleId());
            userRoleMapper.insert(userRole);
        }));
    }
}
