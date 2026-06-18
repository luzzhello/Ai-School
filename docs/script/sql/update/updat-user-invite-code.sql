-- 用户表增加邀请码字段，存量用户回填

ALTER TABLE `sys_user`
    ADD COLUMN `invite_code` VARCHAR(16) DEFAULT NULL COMMENT '邀请码' AFTER `remark`;

-- 存量用户：按 user_id 生成 6 位邀请码（与 InviteCodeUtils 规则一致）
UPDATE `sys_user`
SET `invite_code` = UPPER(RIGHT(LPAD(HEX(`user_id`), 6, '0'), 6))
WHERE `invite_code` IS NULL OR `invite_code` = '';

ALTER TABLE `sys_user`
    ADD UNIQUE KEY `uk_sys_user_invite_code` (`invite_code`);
