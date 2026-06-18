-- 钱包支付宝充值订单

CREATE TABLE IF NOT EXISTS `uc_wallet_pay_order` (
  `order_id`     BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`     VARCHAR(64)    NOT NULL COMMENT '商户订单号',
  `user_id`      BIGINT         NOT NULL COMMENT '用户ID',
  `amount_yuan`  DECIMAL(10, 2) NOT NULL COMMENT '支付金额（元）',
  `coins`        BIGINT         NOT NULL COMMENT '到账金币',
  `status`       CHAR(1)        NOT NULL DEFAULT '0' COMMENT '0待支付 1已支付 2已关闭',
  `pay_channel`  VARCHAR(32)    NOT NULL DEFAULT 'ALIPAY' COMMENT '支付渠道',
  `trade_no`     VARCHAR(64)    DEFAULT NULL COMMENT '支付宝交易号',
  `tenant_id`    VARCHAR(20)    DEFAULT '0' COMMENT '租户',
  `create_time`  DATETIME       DEFAULT NULL,
  `pay_time`     DATETIME       DEFAULT NULL,
  `update_time`  DATETIME       DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_uc_wallet_pay_order_no` (`order_no`),
  KEY `idx_uc_wallet_pay_order_user` (`user_id`, `create_time`)
) ENGINE=InnoDB COMMENT='钱包充值支付订单';
