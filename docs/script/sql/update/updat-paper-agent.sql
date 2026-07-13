-- 论文生成智能体：会话 + 参考文献 + 章节内容

DROP TABLE IF EXISTS `paper_chapter`;
DROP TABLE IF EXISTS `paper_reference`;
DROP TABLE IF EXISTS `paper_session`;

CREATE TABLE `paper_session` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`       VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `user_id`          BIGINT       NOT NULL COMMENT '用户ID',
  `title`            VARCHAR(500) DEFAULT NULL COMMENT '论文题目',
  `status`           VARCHAR(32)  NOT NULL DEFAULT 'init' COMMENT 'init/ref_confirmed/toc_confirmed/writing/done',
  `word_count`       INT          DEFAULT 15000 COMMENT '字数要求',
  `education_level`  VARCHAR(32)  DEFAULT NULL COMMENT '本科/专科',
  `env_info`         VARCHAR(500) DEFAULT NULL COMMENT '开发环境信息',
  `sql_content`      LONGTEXT     DEFAULT NULL COMMENT 'SQL文件内容',
  `code_content`     LONGTEXT     DEFAULT NULL COMMENT 'Controller/Service代码',
  `sql_parsed_json`  LONGTEXT     DEFAULT NULL COMMENT 'SQL解析结果JSON',
  `toc_json`         LONGTEXT     DEFAULT NULL COMMENT '目录大纲JSON',
  `create_time`      DATETIME     DEFAULT NULL,
  `update_time`      DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_session_id` (`session_id`),
  KEY `idx_paper_session_user` (`user_id`, `create_time`)
) ENGINE=InnoDB COMMENT='论文生成智能体会话';

CREATE TABLE `paper_reference` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`  VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `ref_index`   INT          NOT NULL COMMENT '文献序号',
  `author`      VARCHAR(200) DEFAULT NULL,
  `title`       VARCHAR(500) DEFAULT NULL,
  `source`      VARCHAR(300) DEFAULT NULL,
  `year`        INT          DEFAULT NULL,
  `doi`         VARCHAR(200) DEFAULT NULL,
  `type`        VARCHAR(10)  DEFAULT NULL COMMENT 'J/D/M',
  `citation`    TEXT         DEFAULT NULL COMMENT '完整引文',
  `language`    VARCHAR(10)  DEFAULT NULL COMMENT 'zh/en',
  `chapter`     VARCHAR(100) DEFAULT NULL COMMENT '预计插入章节',
  `create_time` DATETIME     DEFAULT NULL,
  `update_time` DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_paper_reference_session` (`session_id`, `ref_index`)
) ENGINE=InnoDB COMMENT='论文参考文献';

CREATE TABLE `paper_chapter` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`    VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `chapter_id`    VARCHAR(64)  NOT NULL COMMENT '章节ID',
  `chapter_title` VARCHAR(200) DEFAULT NULL COMMENT '章节标题',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/generating/done',
  `content`       LONGTEXT     DEFAULT NULL COMMENT '章节正文',
  `create_time`   DATETIME     DEFAULT NULL,
  `update_time`   DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_chapter_session` (`session_id`, `chapter_id`),
  KEY `idx_paper_chapter_session` (`session_id`)
) ENGINE=InnoDB COMMENT='论文章节内容';
