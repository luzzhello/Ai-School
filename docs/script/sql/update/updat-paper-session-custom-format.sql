-- 会话级自定义排版模板（仅绑定当前 paper_session）
-- 可重复执行：按 information_schema 判断列是否存在后再 ADD

SET @db := DATABASE();

-- custom_format_docx_path
SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'paper_session' AND COLUMN_NAME = 'custom_format_docx_path'
    ),
    'SELECT ''skip custom_format_docx_path'' AS msg',
    'ALTER TABLE `paper_session` ADD COLUMN `custom_format_docx_path` varchar(500) DEFAULT NULL COMMENT ''自定义排版docx相对路径'' AFTER `format_override_json`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- custom_format_docx_name
SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'paper_session' AND COLUMN_NAME = 'custom_format_docx_name'
    ),
    'SELECT ''skip custom_format_docx_name'' AS msg',
    'ALTER TABLE `paper_session` ADD COLUMN `custom_format_docx_name` varchar(255) DEFAULT NULL COMMENT ''自定义docx原名'' AFTER `custom_format_docx_path`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- custom_format_docx_size
SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'paper_session' AND COLUMN_NAME = 'custom_format_docx_size'
    ),
    'SELECT ''skip custom_format_docx_size'' AS msg',
    'ALTER TABLE `paper_session` ADD COLUMN `custom_format_docx_size` bigint DEFAULT NULL COMMENT ''自定义docx字节数'' AFTER `custom_format_docx_name`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- custom_format_json
SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'paper_session' AND COLUMN_NAME = 'custom_format_json'
    ),
    'SELECT ''skip custom_format_json'' AS msg',
    'ALTER TABLE `paper_session` ADD COLUMN `custom_format_json` mediumtext DEFAULT NULL COMMENT ''自定义版式主配置JSON'' AFTER `custom_format_docx_size`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- custom_patch_styles
SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'paper_session' AND COLUMN_NAME = 'custom_patch_styles'
    ),
    'SELECT ''skip custom_patch_styles'' AS msg',
    'ALTER TABLE `paper_session` ADD COLUMN `custom_patch_styles` tinyint DEFAULT NULL COMMENT ''1强制patch样式 0不patch'' AFTER `custom_format_json`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
