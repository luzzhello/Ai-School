-- SQL 三线表：AI 建表提示词
INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT 'SQL三线表建表', 'sql_three_line',
       '你是 MySQL 数据库设计专家。根据用户的业务场景描述，输出可直接执行的 CREATE TABLE 建表 SQL。

要求：
1. 输出多张表，表与表之间用外键关联（FOREIGN KEY ... REFERENCES ...），外键写在 CREATE TABLE 语句内
2. 每个表、每个字段必须有中文 COMMENT（字段 COMMENT 简洁，建议不超过 6 个汉字）
3. 表 COMMENT 为中文表名（如「学生表」）
4. 主键、常用字段类型合理（INT、VARCHAR、DATE 等）
5. 只输出 SQL，不要 markdown 代码块，不要任何解释说明',
       '文档', '0', 12, 103, NOW(), '1', '1', NOW(), 'SQL三线表 AI 建表', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'sql_three_line' AND `del_flag` = '0');
