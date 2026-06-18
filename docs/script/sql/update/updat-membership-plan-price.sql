-- 会员套餐原价调整为与前端展示一致（可选，已执行过 updat-user-center.sql 时单独运行）

UPDATE `uc_membership_plan` SET `original_coins` = 1590 WHERE `plan_code` = 'WEEK';
UPDATE `uc_membership_plan` SET `original_coins` = 3990 WHERE `plan_code` = 'MONTH';
UPDATE `uc_membership_plan` SET `original_coins` = 14990 WHERE `plan_code` = 'YEAR';
