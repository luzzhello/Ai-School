-- 会员套餐展示配置 + 后台菜单（与功能定价同属「用户中心配置」目录）

ALTER TABLE `uc_membership_plan`
  ADD COLUMN `display_json` TEXT NULL COMMENT '前台展示 JSON（主题、标签、权益文案等）' AFTER `benefits_json`;

-- 初始化展示配置（与前台默认一致，可在后台「会员配置」调整）
UPDATE `uc_membership_plan` SET `display_json` = '{"theme":"gray","durationSuffix":"/ 永久","description":"注册后默认获得，适合轻度使用用户","benefits":["文件收藏：免费使用","ER图生成：按次付费","AIGC检测：仅免费1次","各类AI功能：免费体验1次"]}' WHERE `plan_code` = 'FREE';
UPDATE `uc_membership_plan` SET `display_json` = '{"theme":"blue","tags":["限时大折扣","即将涨价","新人专享"],"durationSuffix":"/ 7天","description":"适合短期试用，会员专属功能","benefits":["SQL转ER图：无限次","AI生成ER图：15次/天","AIGC检测：6次 / 降AIGC率：10次/天","论文降重：5次/天","各类AI工具：10次/天起","代码生成：9.5折"]}' WHERE `plan_code` = 'WEEK';
UPDATE `uc_membership_plan` SET `display_json` = '{"theme":"purple","topBadge":"热销 · 买一送一","tags":["多数用户选择","买一送一"],"durationSuffix":"/ 30天","description":"性价比之选，每天仅需 0.99 元","specialOffer":"购买即送「启星慧图」体验版一个月","benefits":["SQL转ER图：无限次","AI生成ER图：25次/天","AIGC检测：15次 / 降AIGC率：25次/天","论文降重：20次/天","各类AI工具：20-30次/天","7×24 在线客服 + 优先处理"],"highlight":true}' WHERE `plan_code` = 'MONTH';
UPDATE `uc_membership_plan` SET `display_json` = '{"theme":"orange","topBadge":"最划算","tags":["限时折扣","立省30"],"durationSuffix":"/ 365天","description":"超值之选！每天仅需 0.33 元！","specialOffer":"购买即送「启星慧图」高级版3个月","benefits":["SQL转ER图：无限次","AI生成ER图：50次/天","AIGC检测：30次 / 降AIGC率：50次/天","论文降重：100次/天","各类AI工具：50-100次/天","专属客服 + 新功能优先体验"],"highlight":true}' WHERE `plan_code` = 'YEAR';

-- 父级目录：用户中心配置（功能定价、会员配置）
INSERT INTO `sys_menu` SELECT 2058950000000000000, '用户中心配置', 0, 11, 'usercenter', NULL, '', 1, 0, 'M', '0', '0', '', 'mdi:account-cog', 103, 1, NOW(), 1, NOW(), '用户中心运营配置'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000000);

UPDATE `sys_menu` SET `parent_id` = 2058950000000000000, `order_num` = 1, `path` = 'feature-price'
WHERE `menu_id` = 2058940000000000000;

INSERT INTO `sys_menu` SELECT 2058950000000000001, '会员配置', 2058950000000000000, 2, 'membership-plan', 'usercenter/membership-plan/index', NULL, 1, 0, 'C', '0', '0', 'system:membershipPlan:list', 'mdi:crown', 103, 1, NOW(), 1, NOW(), '会员套餐价格与展示配置'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000001);

INSERT INTO `sys_menu` SELECT 2058950000000000002, '会员配置查询', 2058950000000000001, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipPlan:query', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000002);

INSERT INTO `sys_menu` SELECT 2058950000000000003, '会员配置新增', 2058950000000000001, 2, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipPlan:add', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000003);

INSERT INTO `sys_menu` SELECT 2058950000000000004, '会员配置修改', 2058950000000000001, 3, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipPlan:edit', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000004);

INSERT INTO `sys_menu` SELECT 2058950000000000005, '会员配置删除', 2058950000000000001, 4, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipPlan:remove', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000005);
