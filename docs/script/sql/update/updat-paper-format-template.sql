-- 论文 Word 排版模板表 + paper_session 排版字段
-- 执行前请确认库名；CREATE TABLE 可重复执行；下方 ALTER 若列已存在会报错，请勿重复执行 ALTER 段

CREATE TABLE IF NOT EXISTS `paper_format_template` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`                 VARCHAR(100) NOT NULL COMMENT '模板名称',
  `school_name`          VARCHAR(100) DEFAULT NULL COMMENT '学校备注',
  `is_default`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否全站默认 0/1',
  `status`               CHAR(1)      NOT NULL DEFAULT '1' COMMENT '0停用 1启用',
  `docx_path`            VARCHAR(500) DEFAULT NULL COMMENT '相对上传目录路径',
  `docx_original_name`   VARCHAR(255) DEFAULT NULL,
  `docx_size`            BIGINT       DEFAULT NULL,
  `format_json`          MEDIUMTEXT   NOT NULL COMMENT 'PaperFormatConfig JSON',
  `style_mapping_json`   VARCHAR(2000) DEFAULT NULL COMMENT '样式ID缓存可选',
  `remark`               VARCHAR(500) DEFAULT NULL,
  `create_by`            VARCHAR(64)  DEFAULT NULL,
  `create_time`          DATETIME     DEFAULT NULL,
  `update_by`            VARCHAR(64)  DEFAULT NULL,
  `update_time`          DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论文Word排版模板';

-- 若 format_template_id / format_override_json 已存在，重复执行会失败
ALTER TABLE `paper_session`
  ADD COLUMN `format_template_id` BIGINT NULL COMMENT '排版模板ID' AFTER `education_level`,
  ADD COLUMN `format_override_json` MEDIUMTEXT NULL COMMENT '会话版式覆盖JSON' AFTER `format_template_id`;

-- 默认模板：大连海洋大学（与 PaperFormatDefaults.dalianOcean / PaperFormatMerger.toJson 一致）
INSERT INTO `paper_format_template` (
  `name`,
  `school_name`,
  `is_default`,
  `status`,
  `docx_path`,
  `format_json`,
  `create_time`
)
SELECT
  '大连海洋大学',
  '大连海洋大学',
  1,
  '1',
  NULL,
  '{"page":{"paper":"A4","marginTopMm":30.0,"marginBottomMm":25.0,"marginLeftMm":30.0,"marginRightMm":25.0},"font":{"bodyEastAsia":"宋体","bodyAscii":"Times New Roman","headingEastAsia":"黑体","headingAscii":"Times New Roman","tableEastAsia":"宋体","tableAscii":"Times New Roman","code":"Consolas","footerEastAsia":"宋体"},"fontSize":{"title":18.0,"heading1":16.0,"heading2":12.0,"heading3":10.5,"body":10.5,"abstractLabel":10.5,"caption":9.0,"reference":10.5,"footer":9.0,"toc":10.5},"paragraph":{"lineSpacingPt":18.0,"lineSpacingRule":"exact","lineSpacingMultiple":1.5,"firstLineIndentChars":2,"bodyAlign":"both"},"heading":{"h1Align":"center","h2Align":"left","h3Align":"left","h1Bold":false,"h2Bold":false,"h3Bold":false,"titleBold":true,"h1SpacingBeforePt":12.0,"h1SpacingAfterPt":12.0,"h2SpacingBeforePt":12.0,"h2SpacingAfterPt":12.0,"h3SpacingBeforePt":12.0,"h3SpacingAfterPt":12.0},"export":{"patchTemplateStyles":true,"applyPageSetup":true}}',
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `paper_format_template` WHERE `is_default` = 1
);
