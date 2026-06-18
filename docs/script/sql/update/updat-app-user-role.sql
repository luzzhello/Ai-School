-- 前台用户（app_user）默认角色与接口权限
-- 执行后：新注册用户自动绑 app_user 角色；历史用户见文末迁移语句
-- 权限仅覆盖前台 AiSchoolWeb 所需接口，不包含管理端导出/厂商配置等能力

-- ========== 1. 隐藏目录：前台功能权限（不在管理端侧边栏展示） ==========
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
                        `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
                        `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (2099000000000000001, '前台功能权限', 0, 99, 'app-perms', '', '',
        1, 0, 'M', '1', '0', '', '#',
        103, 1, NOW(), '仅用于前台用户授权，visible=1 隐藏')
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `visible` = '1', `status` = '0';

-- ========== 2. 会话权限（原库可能无 session 菜单，单独补 F 按钮） ==========
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
                        `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
                        `create_dept`, `create_by`, `create_time`, `remark`)
VALUES
(2099000000000000003, '前台-会话列表', 2099000000000000001, 1, '#', '', '', 1, 0, 'F', '1', '0', 'system:session:list', '#', 103, 1, NOW(), ''),
(2099000000000000004, '前台-会话查询', 2099000000000000001, 2, '#', '', '', 1, 0, 'F', '1', '0', 'system:session:query', '#', 103, 1, NOW(), ''),
(2099000000000000005, '前台-会话新增', 2099000000000000001, 3, '#', '', '', 1, 0, 'F', '1', '0', 'system:session:add', '#', 103, 1, NOW(), ''),
(2099000000000000006, '前台-会话修改', 2099000000000000001, 4, '#', '', '', 1, 0, 'F', '1', '0', 'system:session:edit', '#', 103, 1, NOW(), ''),
(2099000000000000007, '前台-会话删除', 2099000000000000001, 5, '#', '', '', 1, 0, 'F', '1', '0', 'system:session:remove', '#', 103, 1, NOW(), ''),
(2099000000000000008, '前台-知识库列表', 2099000000000000001, 6, '#', '', '', 1, 0, 'F', '1', '0', 'system:info:list', '#', 103, 1, NOW(), '')
ON DUPLICATE KEY UPDATE `perms` = VALUES(`perms`), `status` = '0';

-- ========== 3. 前台默认角色（data_scope=5 仅本人） ==========
INSERT INTO `sys_role` (`role_id`, `tenant_id`, `role_name`, `role_key`, `role_sort`, `data_scope`,
                        `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`,
                        `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (2099000000000000002, '000000', '前台用户', 'app_user', 10, '5',
        1, 1, '0', '0',
        103, 1, NOW(), '前台站点默认角色（app_user）')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`),
                        `data_scope` = VALUES(`data_scope`),
                        `status` = '0',
                        `del_flag` = '0';

-- ========== 4. 角色-菜单（含已有对话管理下的模型/消息权限） ==========
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
-- 会话
(2099000000000000002, 2099000000000000003),
(2099000000000000002, 2099000000000000004),
(2099000000000000002, 2099000000000000005),
(2099000000000000002, 2099000000000000006),
(2099000000000000002, 2099000000000000007),
-- 知识库列表
(2099000000000000002, 2099000000000000008),
-- 模型列表（/system/model/modelList）
(2099000000000000002, 2000210913846157314),
-- 聊天消息 CRUD
(2099000000000000002, 2000210914680823809),
(2099000000000000002, 2000210914680823810),
(2099000000000000002, 2000210914680823811),
(2099000000000000002, 2000210914680823812),
(2099000000000000002, 2000210914680823813);

-- ========== 5. 历史前台用户补绑角色（执行 SQL 后需重新登录生效） ==========
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`user_id`, 2099000000000000002
FROM `sys_user` u
WHERE u.`user_type` = 'app_user'
  AND u.`del_flag` = '0'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_user_role` ur
    WHERE ur.`user_id` = u.`user_id`
      AND ur.`role_id` = 2099000000000000002
  );
