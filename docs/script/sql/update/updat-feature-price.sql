-- AI 功能定价配置表 + 默认数据 + 后台菜单

DROP TABLE IF EXISTS `uc_feature_price`;
CREATE TABLE `uc_feature_price` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `feature_code`  VARCHAR(64)  NOT NULL COMMENT '功能编码',
  `feature_name`  VARCHAR(100) NOT NULL COMMENT '功能名称',
  `category`      VARCHAR(32)  NOT NULL COMMENT 'draw/document',
  `price_type`    VARCHAR(16)  NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED固定/PER_THOUSAND按千字',
  `price_coins`   BIGINT       NOT NULL DEFAULT 0 COMMENT '金币价格或每千字金币',
  `status`        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_dept`   BIGINT       DEFAULT NULL,
  `create_by`     BIGINT       DEFAULT NULL,
  `create_time`   DATETIME     DEFAULT NULL,
  `update_by`     BIGINT       DEFAULT NULL,
  `update_time`   DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uc_feature_price_code` (`feature_code`)
) ENGINE=InnoDB COMMENT='AI功能金币定价';

INSERT INTO `uc_feature_price` (`feature_code`, `feature_name`, `category`, `price_type`, `price_coins`, `status`, `sort_order`, `remark`, `create_time`) VALUES
('er_ai',                 'ER图 AI生成',           'draw',     'FIXED',         400, '0', 10, '在线画图', NOW()),
('func_structure_ai',     '功能结构图 AI生成',     'draw',     'FIXED',         400, '0', 20, '在线画图', NOW()),
('mind_map_ai',           '思维导图 AI生成',       'draw',     'FIXED',         300, '0', 30, '在线画图', NOW()),
('system_architecture_ai','系统架构图 AI生成',     'draw',     'FIXED',         400, '0', 40, '在线画图', NOW()),
('software_diagram_ai',   '软件工程图 AI生成',     'draw',     'FIXED',         400, '0', 50, '在线画图', NOW()),
('sql_three_line_ai',     'SQL三线表 AI生成',      'document', 'FIXED',         300, '0', 60, '文档相关', NOW()),
('use_case_spec_ai',      '用例说明 AI生成',       'document', 'FIXED',         300, '0', 70, '文档相关', NOW()),
('func_test_ai',          '功能测试 AI生成',       'document', 'FIXED',         300, '0', 80, '文档相关', NOW()),
('word_table_ai',         'Word表格 AI生成',       'document', 'FIXED',         200, '0', 90, '文档相关', NOW()),
('aigc_detect',           'AIGC检测',              'document', 'PER_THOUSAND',   50, '0', 100, '按千字计费', NOW()),
('aigc_reduce',           'AIGC率降低',            'document', 'PER_THOUSAND',  100, '0', 110, '按千字计费', NOW()),
('thesis_reduce',         '论文降重',              'document', 'PER_THOUSAND',  200, '0', 120, '按千字计费', NOW()),
('er_sql',                'ER图 SQL解析',          'draw',     'FIXED',           0, '0',  11, '在线画图', NOW()),
('sql_three_line_sql',    'SQL三线表 SQL导出',     'document', 'FIXED',           0, '0',  61, '文档相关', NOW()),
('use_case_spec_manual',  '用例说明 手动导出',     'document', 'FIXED',           0, '0',  71, '文档相关', NOW()),
('func_test_manual',      '功能测试 手动导出',     'document', 'FIXED',           0, '0',  81, '文档相关', NOW()),
('word_table_manual',     'Word表格 手动导出',     'document', 'FIXED',           0, '0',  91, '文档相关', NOW()),
('course_code_ai',        '课设代码 AI生成',       'document', 'FIXED',         500, '0', 130, '文档相关', NOW()),
('course_code_sql',       '课设代码 SQL生成',      'document', 'FIXED',         300, '0', 131, '文档相关', NOW());

-- 后台菜单：功能定价
INSERT INTO `sys_menu` VALUES (2058940000000000000, '功能定价', 0, 10, 'feature-price', 'usercenter/feature-price/index', NULL, 1, 0, 'C', '0', '0', 'system:featurePrice:list', 'mdi:currency-cny', 103, 1, NOW(), 1, NOW(), 'AI功能金币定价配置');
INSERT INTO `sys_menu` VALUES (2058940000000000001, '功能定价查询', 2058940000000000000, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:featurePrice:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058940000000000002, '功能定价新增', 2058940000000000000, 2, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:featurePrice:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058940000000000003, '功能定价修改', 2058940000000000000, 3, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:featurePrice:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058940000000000004, '功能定价删除', 2058940000000000000, 4, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:featurePrice:remove', '#', 103, 1, NOW(), NULL, NULL, '');
