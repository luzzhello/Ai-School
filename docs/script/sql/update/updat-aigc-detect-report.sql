-- AIGC 检测报告历史

CREATE TABLE IF NOT EXISTS `aigc_detect_report` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `report_id`    VARCHAR(64)   NOT NULL COMMENT '对外报告ID',
  `user_id`      BIGINT        NOT NULL COMMENT '用户ID',
  `title`        VARCHAR(200)  NOT NULL COMMENT '论文标题',
  `word_count`   INT           NOT NULL DEFAULT 0 COMMENT '字数',
  `aigc_rate`    DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT 'AIGC概率',
  `human_rate`   DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '人工撰写概率',
  `cost_coins`   INT           NOT NULL DEFAULT 0 COMMENT '消耗金币',
  `summary`      VARCHAR(500)  DEFAULT NULL COMMENT '摘要',
  `input_mode`   VARCHAR(16)   DEFAULT NULL COMMENT 'text|file',
  `result_json`  LONGTEXT      NOT NULL COMMENT '完整结果JSON(含segments与正文)',
  `create_time`  DATETIME      DEFAULT NULL,
  `update_time`  DATETIME      DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aigc_detect_report_id` (`report_id`),
  KEY `idx_aigc_detect_report_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AIGC检测报告历史';
