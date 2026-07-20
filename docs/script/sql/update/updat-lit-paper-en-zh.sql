-- 外文文献库：补存知网中译题名/摘要/关键词，供中文检索
-- 已建表环境执行本脚本；新建请直接用 updat-lit-paper-en.sql

ALTER TABLE `lit_paper_en`
  ADD COLUMN `title_zh` VARCHAR(500) DEFAULT NULL COMMENT '知网中译题名' AFTER `keywords`,
  ADD COLUMN `abstract_zh` MEDIUMTEXT DEFAULT NULL COMMENT '知网中译摘要' AFTER `title_zh`,
  ADD COLUMN `keywords_zh` VARCHAR(500) DEFAULT NULL COMMENT '知网中译关键词' AFTER `abstract_zh`;

ALTER TABLE `lit_paper_en`
  ADD FULLTEXT KEY `ft_lit_paper_en_zh` (`title_zh`, `keywords_zh`, `abstract_zh`) WITH PARSER ngram;
