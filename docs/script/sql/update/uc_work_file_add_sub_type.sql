-- uc_work_file 增加子类型字段（软件工程图：class/sequence/activity/usecase 等）
-- 执行前请确认库名；可重复执行时注意 IF NOT EXISTS 仅 MySQL 8.0.29+ 支持部分语法，此处用普通 ALTER

ALTER TABLE `uc_work_file`
    ADD COLUMN `sub_type` varchar(64) NULL DEFAULT NULL COMMENT '子类型（如软件工程图 class/sequence/activity）' AFTER `file_type`;

ALTER TABLE `uc_work_file`
    ADD INDEX `idx_uc_work_file_type_sub`(`file_type`, `sub_type`);

-- 从已有 content_json.diagramType 回填
UPDATE `uc_work_file`
SET `sub_type` = JSON_UNQUOTE(JSON_EXTRACT(`content_json`, '$.diagramType'))
WHERE `file_type` = 'software_diagram'
  AND (`sub_type` IS NULL OR `sub_type` = '')
  AND `content_json` IS NOT NULL
  AND JSON_EXTRACT(`content_json`, '$.diagramType') IS NOT NULL
  AND JSON_TYPE(JSON_EXTRACT(`content_json`, '$.diagramType')) != 'NULL';
