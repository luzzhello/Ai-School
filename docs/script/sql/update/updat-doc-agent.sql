-- 智能文档 Agent：任务定义 + 会话 + 任务执行记录

DROP TABLE IF EXISTS `doc_agent_task_record`;
DROP TABLE IF EXISTS `doc_agent_session`;
DROP TABLE IF EXISTS `doc_agent_task_def`;

CREATE TABLE `doc_agent_task_def` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_code`    VARCHAR(64)  NOT NULL COMMENT '任务编码',
  `task_title`   VARCHAR(100) NOT NULL COMMENT '任务标题',
  `tool_name`    VARCHAR(100) NOT NULL COMMENT '工具名称',
  `task_kind`    VARCHAR(16)  NOT NULL COMMENT 'LLM/DIAGRAM/SQL/PACKAGE',
  `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `remark`       VARCHAR(500) DEFAULT NULL,
  `create_time`  DATETIME     DEFAULT NULL,
  `update_time`  DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_agent_task_code` (`task_code`)
) ENGINE=InnoDB COMMENT='智能文档Agent任务定义';

CREATE TABLE `doc_agent_session` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`         VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `user_id`            BIGINT       NOT NULL COMMENT '用户ID',
  `description`        TEXT         NOT NULL COMMENT '用户需求',
  `model_name`         VARCHAR(100) DEFAULT NULL COMMENT '模型',
  `project_title`      VARCHAR(200) DEFAULT NULL COMMENT '项目标题',
  `status`             VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/STOPPED/FAILED/COMPLETED',
  `charged`            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否已扣费',
  `cost_coins`         BIGINT       NOT NULL DEFAULT 0 COMMENT '扣除金币',
  `failed_task_code`   VARCHAR(64)  DEFAULT NULL COMMENT '失败任务编码',
  `download_file_name` VARCHAR(255) DEFAULT NULL,
  `download_base64`    LONGTEXT     DEFAULT NULL COMMENT 'ZIP Base64',
  `create_time`        DATETIME     DEFAULT NULL,
  `update_time`        DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_agent_session_id` (`session_id`),
  KEY `idx_doc_agent_session_user` (`user_id`, `create_time`)
) ENGINE=InnoDB COMMENT='智能文档Agent会话';

CREATE TABLE `doc_agent_task_record` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`   VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `task_code`    VARCHAR(64)  NOT NULL COMMENT '任务编码',
  `task_title`   VARCHAR(100) NOT NULL COMMENT '任务标题',
  `tool_name`    VARCHAR(100) DEFAULT NULL,
  `task_kind`    VARCHAR(16)  NOT NULL,
  `sort_order`   INT          NOT NULL DEFAULT 0,
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED/STOPPED',
  `content`        LONGTEXT     DEFAULT NULL COMMENT '生成内容',
  `error_msg`      VARCHAR(500) DEFAULT NULL,
  `start_time`     DATETIME     DEFAULT NULL,
  `end_time`       DATETIME     DEFAULT NULL,
  `create_time`    DATETIME     DEFAULT NULL,
  `update_time`    DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_agent_record_session_task` (`session_id`, `task_code`),
  KEY `idx_doc_agent_record_session` (`session_id`)
) ENGINE=InnoDB COMMENT='智能文档Agent任务执行记录';

INSERT INTO `doc_agent_task_def` (`task_code`, `task_title`, `tool_name`, `task_kind`, `sort_order`, `status`, `create_time`) VALUES
('input_summary',           '用户输入总结',       '用户输入总结',       'LLM',      1,  '0', NOW()),
('project_title',           '项目标题生成',       '项目标题生成',       'LLM',      2,  '0', NOW()),
('functional_requirements', '功能要点',           '功能需求分析',       'LLM',      3,  '0', NOW()),
('abstract_write',          '论文摘要总结',       '摘要撰写',           'LLM',      4,  '0', NOW()),
('keywords',                '关键词提取',         '关键词提取',         'LLM',      5,  '0', NOW()),
('references',              '参考文献搜索',       '参考文献检索',       'LLM',      6,  '0', NOW()),
('chapter1',                '第一章 绪论',         '第一章撰写',         'LLM',      7,  '0', NOW()),
('chapter2',                '第二章 关键技术介绍', '第二章撰写',         'LLM',      8,  '0', NOW()),
('chapter3',                '第三章 需求分析',     '第三章撰写',         'LLM',      9,  '0', NOW()),
('func_module_diagram',     '功能模块图生成',     '功能模块图生成',     'DIAGRAM',  10, '0', NOW()),
('architecture_diagram',    '系统架构图生成',     '系统架构图生成',     'DIAGRAM',  11, '0', NOW()),
('chapter4',                '第四章 系统设计',     '第四章撰写',         'LLM',      12, '0', NOW()),
('sql_generate',            'SQL语句生成',         'SQL语句生成',         'SQL',      13, '0', NOW()),
('er_diagram',              'ER图生成',           'ER图生成',           'DIAGRAM',  14, '0', NOW()),
('chapter5',                '第五章 数据库设计',   '第五章撰写',         'LLM',      15, '0', NOW()),
('chapter6',                '第六章 系统实现',     '第六章撰写',         'LLM',      16, '0', NOW()),
('chapter7',                '第七章 系统测试',     '第七章撰写',         'LLM',      17, '0', NOW()),
('conclusion',              '总结与展望',         '结论与展望',         'LLM',      18, '0', NOW()),
('acknowledgement',         '致谢',               '致谢撰写',           'LLM',      19, '0', NOW()),
('packaging',               '整体打包',           '整体打包',           'PACKAGE',  20, '0', NOW());
