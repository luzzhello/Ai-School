-- 用户数据管理：支付订单、钱包、金币流水、云端文件

-- 父级目录：用户数据（与「用户中心配置」「用户反馈」并列）
INSERT INTO `sys_menu` SELECT 2058960000000000000, '用户数据', 0, 10, 'user-data', NULL, '', 1, 0, 'M', '0', '0', '', 'mdi:database-search', 103, 1, NOW(), 1, NOW(), '用户订单、钱包、文件等数据查询'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000000);

-- 支付订单
INSERT INTO `sys_menu` SELECT 2058960000000000001, '支付订单', 2058960000000000000, 1, 'pay-order', 'usercenter/pay-order/index', NULL, 1, 0, 'C', '0', '0', 'system:ucPayOrder:list', 'mdi:credit-card-outline', 103, 1, NOW(), 1, NOW(), '用户支付订单查询'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000001);
INSERT INTO `sys_menu` SELECT 2058960000000000002, '支付订单查询', 2058960000000000001, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucPayOrder:query', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000002);

-- 用户钱包
INSERT INTO `sys_menu` SELECT 2058960000000000003, '用户钱包', 2058960000000000000, 2, 'wallet', 'usercenter/wallet/index', NULL, 1, 0, 'C', '0', '0', 'system:ucWallet:list', 'mdi:wallet-outline', 103, 1, NOW(), 1, NOW(), '用户金币钱包余额查询'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000003);
INSERT INTO `sys_menu` SELECT 2058960000000000004, '用户钱包查询', 2058960000000000003, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucWallet:query', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000004);

-- 金币流水
INSERT INTO `sys_menu` SELECT 2058960000000000005, '金币流水', 2058960000000000000, 3, 'wallet-log', 'usercenter/wallet-log/index', NULL, 1, 0, 'C', '0', '0', 'system:ucWalletLog:list', 'mdi:currency-usd', 103, 1, NOW(), 1, NOW(), '用户金币使用流水查询'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000005);
INSERT INTO `sys_menu` SELECT 2058960000000000006, '金币流水查询', 2058960000000000005, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucWalletLog:query', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000006);

-- 用户文件
INSERT INTO `sys_menu` SELECT 2058960000000000007, '用户文件', 2058960000000000000, 4, 'work-file', 'usercenter/work-file/index', NULL, 1, 0, 'C', '0', '0', 'system:ucWorkFile:list', 'mdi:file-cloud-outline', 103, 1, NOW(), 1, NOW(), '用户云端作品文件查询'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000007);
INSERT INTO `sys_menu` SELECT 2058960000000000008, '用户文件查询', 2058960000000000007, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:ucWorkFile:query', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 2058960000000000008);
