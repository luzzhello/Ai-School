-- 系统实现截图清单（会话级 JSON）
-- 可重复执行：按 information_schema 判断列是否存在后再 ADD

SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'paper_session' AND COLUMN_NAME = 'ui_screenshots_json'
    ),
    'SELECT ''skip ui_screenshots_json'' AS msg',
    'ALTER TABLE `paper_session` ADD COLUMN `ui_screenshots_json` TEXT NULL COMMENT ''系统实现截图清单 JSON'' AFTER `toc_json`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
