-- 放宽 lit_paper 易超长字段（已有表执行）
ALTER TABLE `lit_paper`
  MODIFY COLUMN `authors` VARCHAR(1000) DEFAULT NULL COMMENT '作者/申请者/发布单位',
  MODIFY COLUMN `source`  VARCHAR(1000) DEFAULT NULL COMMENT '刊名/文集名/授予单位/书名来源';
