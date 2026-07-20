-- 论文模板管理菜单（父级：对话管理 2000209300188356609）
INSERT INTO `sys_menu`
SELECT 2058930000000000001, '论文模板', 2000209300188356609, 7, 'paper-template', 'chat/paper-template/index', NULL, 1, 0, 'C', '0', '0', 'system:paperTemplate:list', 'mdi:file-document-edit-outline', 103, 1, NOW(), 1, NOW(), '论文 Word 模板管理'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058930000000000001);

INSERT INTO `sys_menu`
SELECT 2058930000000000002, '论文模板查询', 2058930000000000001, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:paperTemplate:query', '#', 103, 1, NOW(), NULL, NULL, ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058930000000000002);

INSERT INTO `sys_menu`
SELECT 2058930000000000003, '论文模板上传', 2058930000000000001, 2, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:paperTemplate:upload', '#', 103, 1, NOW(), NULL, NULL, ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058930000000000003);

INSERT INTO `sys_menu`
SELECT 2058930000000000004, '论文模板重置', 2058930000000000001, 3, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:paperTemplate:reset', '#', 103, 1, NOW(), NULL, NULL, ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058930000000000004);
