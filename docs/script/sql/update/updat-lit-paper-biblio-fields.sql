-- 已有 lit_paper 表时补充著录字段（幂等：列已存在会报错，可忽略该条）
-- 执行前请确认表已存在；新建库直接用 updat-lit-paper.sql 全量建表即可。

ALTER TABLE `lit_paper`
  ADD COLUMN `volume`         VARCHAR(64)  DEFAULT NULL COMMENT '卷号' AFTER `year`,
  ADD COLUMN `issue`          VARCHAR(64)  DEFAULT NULL COMMENT '期号' AFTER `volume`,
  ADD COLUMN `pages`          VARCHAR(100) DEFAULT NULL COMMENT '起-止页码' AFTER `issue`,
  ADD COLUMN `publisher`      VARCHAR(200) DEFAULT NULL COMMENT '出版者' AFTER `pages`,
  ADD COLUMN `publish_place`  VARCHAR(200) DEFAULT NULL COMMENT '出版地' AFTER `publisher`,
  ADD COLUMN `translator`     VARCHAR(200) DEFAULT NULL COMMENT '译者' AFTER `publish_place`,
  ADD COLUMN `degree`         VARCHAR(100) DEFAULT NULL COMMENT '学位类型' AFTER `translator`,
  ADD COLUMN `degree_place`   VARCHAR(200) DEFAULT NULL COMMENT '授予单位所在地' AFTER `degree`,
  ADD COLUMN `patent_country` VARCHAR(100) DEFAULT NULL COMMENT '专利国名' AFTER `degree_place`,
  ADD COLUMN `patent_kind`    VARCHAR(100) DEFAULT NULL COMMENT '专利文献种类' AFTER `patent_country`,
  ADD COLUMN `patent_no`      VARCHAR(100) DEFAULT NULL COMMENT '专利号' AFTER `patent_kind`,
  ADD COLUMN `standard_code`  VARCHAR(100) DEFAULT NULL COMMENT '技术标准代号' AFTER `patent_no`,
  ADD COLUMN `publish_date`   VARCHAR(64)  DEFAULT NULL COMMENT '专利/标准出版日期' AFTER `standard_code`;

-- doc_type 注释扩展为含 P/S（列本身无需改类型）
ALTER TABLE `lit_paper` MODIFY COLUMN `doc_type` VARCHAR(10) DEFAULT NULL COMMENT 'J/D/C/M/P/S';
