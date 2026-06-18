-- 前台用户类型说明（无需改表，sys_user.user_type 已存在）
-- sys_user  : 后台管理用户（管理端创建 / 原 /auth/register）
-- app_user  : 前台站点用户（/auth/front/register 邮箱或手机注册、微信快捷登录）

-- 可选：将历史前台误标为 sys_user 的微信用户改为 app_user（按需执行）
-- UPDATE sys_user SET user_type = 'app_user' WHERE open_id IS NOT NULL AND user_type = 'sys_user';
