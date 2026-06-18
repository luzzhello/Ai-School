-- 扩展支付订单：支持会员购买、扫码支付、订单列表

ALTER TABLE `uc_wallet_pay_order`
  ADD COLUMN `order_type`    VARCHAR(32)  NOT NULL DEFAULT 'RECHARGE' COMMENT 'RECHARGE充值 MEMBERSHIP会员' AFTER `order_no`,
  ADD COLUMN `order_name`    VARCHAR(100) DEFAULT '' COMMENT '订单名称' AFTER `coins`,
  ADD COLUMN `order_content` VARCHAR(500) DEFAULT '' COMMENT '订单描述' AFTER `order_name`,
  ADD COLUMN `plan_code`     VARCHAR(32)  DEFAULT NULL COMMENT '会员套餐编码' AFTER `order_content`,
  ADD COLUMN `coins_used`    BIGINT       NOT NULL DEFAULT 0 COMMENT '已抵扣金币' AFTER `plan_code`,
  ADD COLUMN `total_coins`   BIGINT       NOT NULL DEFAULT 0 COMMENT '订单总金币（会员）' AFTER `coins_used`,
  ADD COLUMN `qr_code`       VARCHAR(512) DEFAULT NULL COMMENT '支付宝扫码内容' AFTER `trade_no`,
  ADD COLUMN `expire_time`   DATETIME     DEFAULT NULL COMMENT '支付过期时间' AFTER `update_time`;

-- status: 0待支付 1已支付 2已关闭 3已过期
