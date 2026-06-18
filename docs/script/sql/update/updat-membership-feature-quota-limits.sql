-- 会员功能每日次数限额（数值，供运行时配额校验；展示文案仍用 week_text 等字段）

ALTER TABLE `uc_membership_feature_quota`
  ADD COLUMN `week_limit`  INT DEFAULT NULL COMMENT '周会员每日次数，-1无限，NULL无会员配额' AFTER `year_text`,
  ADD COLUMN `month_limit` INT DEFAULT NULL COMMENT '月会员每日次数，-1无限，NULL无会员配额' AFTER `week_limit`,
  ADD COLUMN `year_limit`  INT DEFAULT NULL COMMENT '年会员每日次数，-1无限，NULL无会员配额' AFTER `month_limit`;

UPDATE `uc_membership_feature_quota` SET `feature_code` = 'er_sql',            `week_limit` = -1, `month_limit` = -1, `year_limit` = -1  WHERE `feature_name` = 'SQL转ER图' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'er_ai',             `week_limit` = 15, `month_limit` = 25, `year_limit` = 35  WHERE `feature_name` = 'AI生成ER图' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'aigc_detect',       `week_limit` = 6,  `month_limit` = 15, `year_limit` = 30  WHERE `feature_name` = 'AIGC检测' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'aigc_reduce',       `week_limit` = 6,  `month_limit` = 15, `year_limit` = 50  WHERE `feature_name` = '降AIGC率' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'thesis_reduce',     `week_limit` = 5,  `month_limit` = 20, `year_limit` = 100 WHERE `feature_name` = '论文降重' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'func_structure_ai', `week_limit` = 10, `month_limit` = 25, `year_limit` = 50  WHERE `feature_name` = 'AI生成功能结构图' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'software_diagram_ai', `week_limit` = 10, `month_limit` = 30, `year_limit` = 100 WHERE `feature_name` = 'AI智能生成图谱' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'use_case_spec_ai', `week_limit` = 10, `month_limit` = 30, `year_limit` = 100 WHERE `feature_name` = 'AI生成用例文档' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'mind_map_ai',       `week_limit` = 10, `month_limit` = 20, `year_limit` = 100 WHERE `feature_name` = 'AI生成思维导图' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'word_table_ai',   `week_limit` = 10, `month_limit` = 20, `year_limit` = 100 WHERE `feature_name` = 'Word表格生成' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'sql_three_line_ai', `week_limit` = 10, `month_limit` = 25, `year_limit` = 50 WHERE `feature_name` = 'SQL转三线表' AND `is_category` = '0';
UPDATE `uc_membership_feature_quota` SET `feature_code` = 'func_test_ai',     `week_limit` = 10, `month_limit` = 30, `year_limit` = 100 WHERE `feature_name` = '功能测试文档' AND `is_category` = '0';

-- 系统架构图与部分 manual 变体（与 AI 版共用限额时可单独配置）
INSERT INTO `uc_membership_feature_quota`
(`feature_name`, `feature_code`, `free_text`, `week_text`, `month_text`, `year_text`,
 `week_limit`, `month_limit`, `year_limit`, `is_category`, `sort_order`, `status`, `create_time`)
SELECT '系统架构图AI', 'system_architecture_ai', '20金币/次', '10次/天', '30次/天', '100次/天',
       10, 30, 100, '0', 33, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `uc_membership_feature_quota` WHERE `feature_code` = 'system_architecture_ai');

INSERT INTO `uc_membership_feature_quota`
(`feature_name`, `feature_code`, `free_text`, `week_text`, `month_text`, `year_text`,
 `week_limit`, `month_limit`, `year_limit`, `is_category`, `sort_order`, `status`, `create_time`)
SELECT '用例文档(手动)', 'use_case_spec_manual', '20金币/次', '10次/天', '30次/天', '100次/天',
       10, 30, 100, '0', 34, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `uc_membership_feature_quota` WHERE `feature_code` = 'use_case_spec_manual');

INSERT INTO `uc_membership_feature_quota`
(`feature_name`, `feature_code`, `free_text`, `week_text`, `month_text`, `year_text`,
 `week_limit`, `month_limit`, `year_limit`, `is_category`, `sort_order`, `status`, `create_time`)
SELECT 'Word表格(手动)', 'word_table_manual', '20金币/次', '10次/天', '20次/天', '100次/天',
       10, 20, 100, '0', 37, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `uc_membership_feature_quota` WHERE `feature_code` = 'word_table_manual');

INSERT INTO `uc_membership_feature_quota`
(`feature_name`, `feature_code`, `free_text`, `week_text`, `month_text`, `year_text`,
 `week_limit`, `month_limit`, `year_limit`, `is_category`, `sort_order`, `status`, `create_time`)
SELECT 'SQL三线表(SQL)', 'sql_three_line_sql', '1个节点2分钱', '10次/天', '25次/天', '50次/天',
       10, 25, 50, '0', 42, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `uc_membership_feature_quota` WHERE `feature_code` = 'sql_three_line_sql');

INSERT INTO `uc_membership_feature_quota`
(`feature_name`, `feature_code`, `free_text`, `week_text`, `month_text`, `year_text`,
 `week_limit`, `month_limit`, `year_limit`, `is_category`, `sort_order`, `status`, `create_time`)
SELECT '功能测试(手动)', 'func_test_manual', '30金币/次', '10次/天', '30次/天', '100次/天',
       10, 30, 100, '0', 43, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `uc_membership_feature_quota` WHERE `feature_code` = 'func_test_manual');
