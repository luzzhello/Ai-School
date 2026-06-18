-- 会员功能每日使用次数记录

CREATE TABLE IF NOT EXISTS `uc_feature_daily_usage` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
  `feature_code` VARCHAR(64)  NOT NULL COMMENT '功能编码',
  `usage_date`   DATE         NOT NULL COMMENT '使用日期',
  `use_count`    INT          NOT NULL DEFAULT 0 COMMENT '当日已用次数',
  `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uc_feature_daily_usage` (`user_id`, `feature_code`, `usage_date`),
  KEY `idx_uc_feature_daily_usage_user_date` (`user_id`, `usage_date`)
) ENGINE=InnoDB COMMENT='会员功能每日使用次数';
