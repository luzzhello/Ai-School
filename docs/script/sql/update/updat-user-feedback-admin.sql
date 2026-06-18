-- 用户反馈管理：审核字段 + 后台菜单

ALTER TABLE `uc_activity_submission`
  ADD COLUMN `audit_remark` VARCHAR(500) DEFAULT NULL COMMENT '审核备注' AFTER `reward_coins`,
  ADD COLUMN `audit_by` BIGINT DEFAULT NULL COMMENT '审核人' AFTER `audit_remark`,
  ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间' AFTER `audit_by`;

-- 顶级目录：用户反馈
INSERT INTO `sys_menu` VALUES (2058930000000000000, '用户反馈', 0, 9, 'user-feedback', '', NULL, 1, 0, 'M', '0', '0', '', 'mdi:message-alert-outline', 103, 1, NOW(), 1, NOW(), '用户反馈目录');

-- 分享申请
INSERT INTO `sys_menu` VALUES (2058930000000000001, '分享申请', 2058930000000000000, 1, 'share', 'usercenter/share/index', NULL, 1, 0, 'C', '0', '0', 'system:ucShare:list', 'mdi:share-variant-outline', 103, 1, NOW(), 1, NOW(), '用户分享申请审核');
INSERT INTO `sys_menu` VALUES (2058930000000000002, '分享申请查询', 2058930000000000001, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucShare:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058930000000000003, '分享申请审核', 2058930000000000001, 2, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucShare:audit', '#', 103, 1, NOW(), NULL, NULL, '');

-- Bug反馈
INSERT INTO `sys_menu` VALUES (2058930000000000004, 'Bug反馈', 2058930000000000000, 2, 'bug', 'usercenter/bug/index', NULL, 1, 0, 'C', '0', '0', 'system:ucBug:list', 'mdi:bug-outline', 103, 1, NOW(), 1, NOW(), '用户Bug反馈审核');
INSERT INTO `sys_menu` VALUES (2058930000000000005, 'Bug反馈查询', 2058930000000000004, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucBug:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058930000000000006, 'Bug反馈审核', 2058930000000000004, 2, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucBug:audit', '#', 103, 1, NOW(), NULL, NULL, '');

-- 邀请记录
INSERT INTO `sys_menu` VALUES (2058930000000000007, '邀请记录', 2058930000000000000, 3, 'invite', 'usercenter/invite/index', NULL, 1, 0, 'C', '0', '0', 'system:ucInvite:list', 'mdi:account-multiple-plus-outline', 103, 1, NOW(), 1, NOW(), '用户邀请绑定记录');
INSERT INTO `sys_menu` VALUES (2058930000000000008, '邀请记录查询', 2058930000000000007, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucInvite:query', '#', 103, 1, NOW(), NULL, NULL, '');
