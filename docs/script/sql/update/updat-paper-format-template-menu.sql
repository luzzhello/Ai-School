-- 论文排版模板菜单（对话管理下）
-- 父菜单：对话管理 menu_id = 2000209300188356609
-- 可重复执行：按 path / perms 防重

-- 页面菜单
INSERT INTO `sys_menu` (
  `menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
  `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
  `create_dept`, `create_by`, `create_time`, `remark`
)
SELECT
  2099010000000000001,
  '论文排版模板',
  2000209300188356609,
  8,
  'paper-format-template',
  'chat/paper-format-template/index',
  '',
  1,
  0,
  'C',
  '0',
  '0',
  'system:paperFormatTemplate:list',
  'mdi:format-font',
  103,
  1,
  NOW(),
  '多套学校 Word 排版模板（docx + format_json）'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `path` = 'paper-format-template' AND `parent_id` = 2000209300188356609
);

-- 按钮权限（挂在本页菜单下）
INSERT INTO `sys_menu` (
  `menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
  `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
  `create_dept`, `create_by`, `create_time`, `remark`
)
SELECT
  2099010000000000002,
  '排版模板查询',
  2099010000000000001,
  1,
  '#',
  '',
  '',
  1,
  0,
  'F',
  '0',
  '0',
  'system:paperFormatTemplate:query',
  '#',
  103,
  1,
  NOW(),
  ''
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `perms` = 'system:paperFormatTemplate:query'
);

INSERT INTO `sys_menu` (
  `menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
  `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
  `create_dept`, `create_by`, `create_time`, `remark`
)
SELECT
  2099010000000000003,
  '排版模板新增',
  2099010000000000001,
  2,
  '#',
  '',
  '',
  1,
  0,
  'F',
  '0',
  '0',
  'system:paperFormatTemplate:add',
  '#',
  103,
  1,
  NOW(),
  ''
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `perms` = 'system:paperFormatTemplate:add'
);

INSERT INTO `sys_menu` (
  `menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
  `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
  `create_dept`, `create_by`, `create_time`, `remark`
)
SELECT
  2099010000000000004,
  '排版模板修改',
  2099010000000000001,
  3,
  '#',
  '',
  '',
  1,
  0,
  'F',
  '0',
  '0',
  'system:paperFormatTemplate:edit',
  '#',
  103,
  1,
  NOW(),
  ''
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `perms` = 'system:paperFormatTemplate:edit'
);

INSERT INTO `sys_menu` (
  `menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
  `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
  `create_dept`, `create_by`, `create_time`, `remark`
)
SELECT
  2099010000000000005,
  '排版模板上传',
  2099010000000000001,
  4,
  '#',
  '',
  '',
  1,
  0,
  'F',
  '0',
  '0',
  'system:paperFormatTemplate:upload',
  '#',
  103,
  1,
  NOW(),
  ''
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `perms` = 'system:paperFormatTemplate:upload'
);

-- 授权给超级管理员 / 管理员（按 menu_id 防重）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.menu_id
FROM `sys_menu` m
WHERE m.menu_id BETWEEN 2099010000000000001 AND 2099010000000000005
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2018858143199662082, m.menu_id
FROM `sys_menu` m
WHERE m.menu_id BETWEEN 2099010000000000001 AND 2099010000000000005
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm
    WHERE rm.role_id = 2018858143199662082 AND rm.menu_id = m.menu_id
  );
