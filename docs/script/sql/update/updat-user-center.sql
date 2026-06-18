-- 个人中心：钱包（金币）、会员、云端作品文件

-- 用户钱包（1 用户 1 条）
DROP TABLE IF EXISTS `uc_wallet`;
CREATE TABLE `uc_wallet` (
  `wallet_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '钱包ID',
  `user_id`        BIGINT       NOT NULL COMMENT '用户ID',
  `balance`        BIGINT       NOT NULL DEFAULT 0 COMMENT '金币余额',
  `frozen_balance` BIGINT       NOT NULL DEFAULT 0 COMMENT '冻结金币',
  `version`        BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `tenant_id`      VARCHAR(20)  DEFAULT '000000' COMMENT '租户编号',
  `create_dept`    BIGINT       DEFAULT NULL COMMENT '创建部门',
  `create_by`      BIGINT       DEFAULT NULL COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_by`      BIGINT       DEFAULT NULL COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`wallet_id`),
  UNIQUE KEY `uk_uc_wallet_user` (`user_id`)
) ENGINE=InnoDB COMMENT='用户金币钱包';

-- 金币流水
DROP TABLE IF EXISTS `uc_wallet_log`;
CREATE TABLE `uc_wallet_log` (
  `log_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id`        BIGINT       NOT NULL COMMENT '用户ID',
  `change_amount`  BIGINT       NOT NULL COMMENT '变动数量（正负）',
  `balance_after`  BIGINT       NOT NULL COMMENT '变动后余额',
  `biz_type`       VARCHAR(32)  NOT NULL COMMENT '业务类型 RECHARGE/MEMBERSHIP_BUY/MEMBERSHIP_REFUND/TOOL_CONSUME/GIFT/CHECK_IN',
  `biz_no`         VARCHAR(64)  DEFAULT NULL COMMENT '关联业务单号',
  `description`    VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `tenant_id`      VARCHAR(20)  DEFAULT '000000' COMMENT '租户编号',
  `create_dept`    BIGINT       DEFAULT NULL COMMENT '创建部门',
  `create_by`      BIGINT       DEFAULT NULL COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_by`      BIGINT       DEFAULT NULL COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_uc_wallet_log_user` (`user_id`, `create_time`)
) ENGINE=InnoDB COMMENT='金币流水';

-- 会员套餐（字典/商品）
DROP TABLE IF EXISTS `uc_membership_plan`;
CREATE TABLE `uc_membership_plan` (
  `plan_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
  `plan_code`      VARCHAR(32)  NOT NULL COMMENT '套餐编码 FREE/WEEK/MONTH/YEAR',
  `plan_name`      VARCHAR(64)  NOT NULL COMMENT '套餐名称',
  `price_coins`    BIGINT       NOT NULL DEFAULT 0 COMMENT '售价（金币）',
  `original_coins` BIGINT       DEFAULT NULL COMMENT '原价（展示用）',
  `duration_days`  INT          NOT NULL DEFAULT 0 COMMENT '有效天数，0=免费',
  `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `benefits_json`  TEXT         COMMENT '权益 JSON',
  `status`         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `remark`         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_dept`    BIGINT       DEFAULT NULL COMMENT '创建部门',
  `create_by`      BIGINT       DEFAULT NULL COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_by`      BIGINT       DEFAULT NULL COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`plan_id`),
  UNIQUE KEY `uk_uc_plan_code` (`plan_code`)
) ENGINE=InnoDB COMMENT='会员套餐';

INSERT INTO `uc_membership_plan` (`plan_code`, `plan_name`, `price_coins`, `original_coins`, `duration_days`, `sort_order`, `benefits_json`, `status`, `create_time`) VALUES
('FREE',  '免费会员', 0,    0,     0,   1, '{"sqlLimit":"有限","aiDaily":3,"fileLimit":10}', '0', NOW()),
('WEEK',  '周会员',   990,  1590,  7,   2, '{"sqlLimit":"无限","aiDaily":15,"fileLimit":50}', '0', NOW()),
('MONTH', '月会员',   2990, 3990,  30,  3, '{"sqlLimit":"无限","aiDaily":25,"fileLimit":200}', '0', NOW()),
('YEAR',  '年会员',   9990, 14990, 365, 4, '{"sqlLimit":"无限","aiDaily":50,"fileLimit":1000}', '0', NOW());

-- 用户会员订阅
DROP TABLE IF EXISTS `uc_user_membership`;
CREATE TABLE `uc_user_membership` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT       NOT NULL COMMENT '用户ID',
  `plan_code`      VARCHAR(32)  NOT NULL COMMENT '套餐编码',
  `plan_name`      VARCHAR(64)  NOT NULL COMMENT '套餐名称',
  `start_time`     DATETIME     NOT NULL COMMENT '开始时间',
  `expire_time`    DATETIME     DEFAULT NULL COMMENT '到期时间，NULL 表示免费永久',
  `status`         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0有效 1过期',
  `tenant_id`      VARCHAR(20)  DEFAULT '000000' COMMENT '租户编号',
  `create_dept`    BIGINT       DEFAULT NULL COMMENT '创建部门',
  `create_by`      BIGINT       DEFAULT NULL COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_by`      BIGINT       DEFAULT NULL COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_uc_membership_user` (`user_id`, `expire_time`)
) ENGINE=InnoDB COMMENT='用户会员';

-- 云端作品文件
DROP TABLE IF EXISTS `uc_work_file`;
CREATE TABLE `uc_work_file` (
  `file_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `user_id`        BIGINT       NOT NULL COMMENT '用户ID',
  `file_name`      VARCHAR(200) NOT NULL COMMENT '文件名',
  `description`    VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `file_type`      VARCHAR(32)  NOT NULL COMMENT 'er/func_structure/mind_map/...',
  `thumbnail`      MEDIUMTEXT   DEFAULT NULL COMMENT '封面 base64 或 URL',
  `content_json`   LONGTEXT     DEFAULT NULL COMMENT '作品 JSON 快照',
  `file_size`      BIGINT       DEFAULT 0 COMMENT '字节',
  `storage_type`   VARCHAR(16)  NOT NULL DEFAULT 'cloud' COMMENT 'cloud',
  `tenant_id`      VARCHAR(20)  DEFAULT '000000' COMMENT '租户编号',
  `create_dept`    BIGINT       DEFAULT NULL COMMENT '创建部门',
  `create_by`      BIGINT       DEFAULT NULL COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_by`      BIGINT       DEFAULT NULL COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
  `del_flag`       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (`file_id`),
  KEY `idx_uc_work_file_user` (`user_id`, `update_time`)
) ENGINE=InnoDB COMMENT='用户云端作品';
