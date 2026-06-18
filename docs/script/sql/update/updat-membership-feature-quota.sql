-- 会员功能费用/次数配额表 + 默认数据 + 后台按钮权限

DROP TABLE IF EXISTS `uc_membership_feature_quota`;
CREATE TABLE `uc_membership_feature_quota` (
  `quota_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `feature_name` VARCHAR(128) NOT NULL COMMENT '功能名称',
  `feature_code` VARCHAR(64)  DEFAULT NULL COMMENT '功能编码（可选，预留校验）',
  `free_text`    VARCHAR(256) DEFAULT '' COMMENT '免费用户展示',
  `week_text`    VARCHAR(256) DEFAULT '' COMMENT '周会员展示',
  `month_text`   VARCHAR(256) DEFAULT '' COMMENT '月会员展示',
  `year_text`    VARCHAR(256) DEFAULT '' COMMENT '年会员展示',
  `week_limit`   INT          DEFAULT NULL COMMENT '周会员每日次数，-1无限，NULL无会员配额',
  `month_limit`  INT          DEFAULT NULL COMMENT '月会员每日次数，-1无限，NULL无会员配额',
  `year_limit`   INT          DEFAULT NULL COMMENT '年会员每日次数，-1无限，NULL无会员配额',
  `is_category`  CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否分类行 0否 1是',
  `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_dept`  BIGINT       DEFAULT NULL,
  `create_by`    BIGINT       DEFAULT NULL,
  `create_time`  DATETIME     DEFAULT NULL,
  `update_by`    BIGINT       DEFAULT NULL,
  `update_time`  DATETIME     DEFAULT NULL,
  PRIMARY KEY (`quota_id`),
  KEY `idx_uc_mfq_sort` (`sort_order`)
) ENGINE=InnoDB COMMENT='会员功能费用与次数配额';

INSERT INTO `uc_membership_feature_quota`
(`feature_name`, `free_text`, `week_text`, `month_text`, `year_text`, `is_category`, `sort_order`, `status`, `create_time`) VALUES
('ER图工具', '', '', '', '', '1', 10, '0', NOW()),
('SQL转ER图', '(节点×4)金币/次', '无限次', '无限次', '无限次', '0', 11, '0', NOW()),
('AI生成ER图', '(50+字数×5)金币/次', '15次/天', '25次/天', '35次/天', '0', 12, '0', NOW()),
('论文工具', '', '', '', '', '1', 20, '0', NOW()),
('AIGC检测', '(50/千字)金币/次（仅1次免费）', '6次/天', '15次/天', '30次/天', '0', 21, '0', NOW()),
('降AIGC率', '(100/千字)金币/次（仅1次免费）', '6次/天', '15次/天', '50次/天', '0', 22, '0', NOW()),
('论文降重', '(200/千字)金币/次', '5次/天', '20次/天', '100次/天', '0', 23, '0', NOW()),
('AI生成工具', '', '', '', '', '1', 30, '0', NOW()),
('AI生成功能结构图', '20金币/次', '10次/天', '25次/天', '50次/天', '0', 31, '0', NOW()),
('AI智能生成图谱', '20金币/次', '10次/天', '30次/天', '100次/天', '0', 32, '0', NOW()),
('AI生成用例文档', '20金币/次', '10次/天', '30次/天', '100次/天', '0', 33, '0', NOW()),
('AI生成思维导图', '20金币/次', '10次/天', '20次/天', '100次/天', '0', 34, '0', NOW()),
('AI生成海报卡片', '20金币/次', '10次/天', '20次/天', '100次/天', '0', 35, '0', NOW()),
('Word表格生成', '20金币/次', '10次/天', '20次/天', '100次/天', '0', 36, '0', NOW()),
('文档与转换', '', '', '', '', '1', 40, '0', NOW()),
('SQL转三线表', '1个节点2分钱', '10次/天', '25次/天', '50次/天', '0', 41, '0', NOW()),
('功能测试文档', '30金币/次', '10次/天', '30次/天', '100次/天', '0', 42, '0', NOW()),
('课设/毕业代码生成', '15金币/次', '9.5折', '9折', '8折', '0', 43, '0', NOW());

-- 按钮权限挂在「会员配置」菜单下
INSERT INTO `sys_menu` SELECT 2058950000000000009, '功能配额列表', 2058950000000000001, 5, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipFeatureQuota:list', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000009);

INSERT INTO `sys_menu` SELECT 2058950000000000010, '功能配额查询', 2058950000000000001, 6, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipFeatureQuota:query', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000010);

INSERT INTO `sys_menu` SELECT 2058950000000000011, '功能配额新增', 2058950000000000001, 7, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipFeatureQuota:add', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000011);

INSERT INTO `sys_menu` SELECT 2058950000000000012, '功能配额修改', 2058950000000000001, 8, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipFeatureQuota:edit', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000012);

INSERT INTO `sys_menu` SELECT 2058950000000000013, '功能配额删除', 2058950000000000001, 9, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:membershipFeatureQuota:remove', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058950000000000013);
