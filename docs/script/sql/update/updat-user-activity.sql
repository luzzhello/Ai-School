-- 活动中心：签到、邀请、反馈提交、兑换码

DROP TABLE IF EXISTS `uc_check_in_log`;
CREATE TABLE `uc_check_in_log` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
  `check_date`  DATE        NOT NULL COMMENT '签到日期',
  `coins`       BIGINT      NOT NULL DEFAULT 1 COMMENT '获得金币',
  `streak`      INT         NOT NULL DEFAULT 1 COMMENT '连续签到天数',
  `create_time` DATETIME    DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uc_check_in_user_date` (`user_id`, `check_date`),
  KEY `idx_uc_check_in_user` (`user_id`, `check_date`)
) ENGINE=InnoDB COMMENT='每日签到记录';

DROP TABLE IF EXISTS `uc_invite_bind`;
CREATE TABLE `uc_invite_bind` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `invitee_id`     BIGINT      NOT NULL COMMENT '被邀请人用户ID',
  `inviter_id`     BIGINT      NOT NULL COMMENT '邀请人用户ID',
  `invite_code`    VARCHAR(16) NOT NULL COMMENT '使用的邀请码',
  `coins_inviter`  BIGINT      NOT NULL DEFAULT 0 COMMENT '邀请人获得金币',
  `coins_invitee`  BIGINT      NOT NULL DEFAULT 0 COMMENT '被邀请人获得金币',
  `month_key`      VARCHAR(7)  NOT NULL COMMENT '月份 yyyy-MM',
  `create_time`    DATETIME    DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uc_invite_invitee` (`invitee_id`),
  KEY `idx_uc_invite_inviter_month` (`inviter_id`, `month_key`)
) ENGINE=InnoDB COMMENT='邀请绑定记录';

DROP TABLE IF EXISTS `uc_activity_submission`;
CREATE TABLE `uc_activity_submission` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
  `activity_type` VARCHAR(16)  NOT NULL COMMENT 'SHARE/BUG',
  `feedback_type` VARCHAR(32)  DEFAULT NULL COMMENT 'PROBLEM/TOOL_SHARE/NEED',
  `subtype`       VARCHAR(64)  DEFAULT NULL COMMENT '具体子类型',
  `related_apps`  VARCHAR(500) DEFAULT NULL COMMENT '相关应用，逗号分隔',
  `contact`       VARCHAR(128) DEFAULT NULL COMMENT '联系方式',
  `content`       TEXT         COMMENT '反馈内容',
  `images_json`   TEXT         COMMENT '图片 JSON 数组',
  `remark`        TEXT         COMMENT '补充说明',
  `status`        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0待审核 1已通过 2已拒绝',
  `reward_coins`  BIGINT       NOT NULL DEFAULT 0 COMMENT '奖励金币',
  `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_uc_activity_sub_user` (`user_id`, `create_time`)
) ENGINE=InnoDB COMMENT='活动反馈/分享提交';

DROP TABLE IF EXISTS `uc_redeem_code`;
CREATE TABLE `uc_redeem_code` (
  `code_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`         VARCHAR(32)  NOT NULL COMMENT '兑换码',
  `coins`        BIGINT       NOT NULL COMMENT '兑换金币数',
  `max_uses`     INT          NOT NULL DEFAULT 1 COMMENT '最大使用次数',
  `used_count`   INT          NOT NULL DEFAULT 0 COMMENT '已使用次数',
  `expire_time`  DATETIME     DEFAULT NULL COMMENT '过期时间',
  `status`       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `remark`       VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`code_id`),
  UNIQUE KEY `uk_uc_redeem_code` (`code`)
) ENGINE=InnoDB COMMENT='兑换码';

DROP TABLE IF EXISTS `uc_redeem_log`;
CREATE TABLE `uc_redeem_log` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
  `code_id`     BIGINT      NOT NULL COMMENT '兑换码ID',
  `code`        VARCHAR(32) NOT NULL COMMENT '兑换码',
  `coins`       BIGINT      NOT NULL COMMENT '获得金币',
  `create_time` DATETIME    DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uc_redeem_user_code` (`user_id`, `code_id`)
) ENGINE=InnoDB COMMENT='兑换记录';

INSERT INTO `uc_redeem_code` (`code`, `coins`, `max_uses`, `used_count`, `expire_time`, `status`, `remark`, `create_time`) VALUES
('WELCOME100', 100, 9999, 0, DATE_ADD(NOW(), INTERVAL 1 YEAR), '0', '新用户欢迎礼包', NOW()),
('AISCHOOL50', 50, 9999, 0, DATE_ADD(NOW(), INTERVAL 1 YEAR), '0', '活动体验码', NOW());
