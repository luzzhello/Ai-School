-- AI提示词模块
DROP TABLE IF EXISTS `chat_prompt`;
CREATE TABLE `chat_prompt` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `prompt_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词名称',
    `prompt_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词编码',
    `prompt_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词内容',
    `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类/场景',
    `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `sort_order` int NULL DEFAULT 0 COMMENT '排序',
    `create_dept` bigint NULL DEFAULT NULL COMMENT '创建部门',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
    `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
    `version` int NULL DEFAULT NULL COMMENT '版本',
    `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户Id',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `unique_prompt_code`(`prompt_code` ASC, `tenant_id` ASC, `del_flag` ASC) USING BTREE,
    INDEX `idx_category`(`category` ASC) USING BTREE,
    INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI提示词表' ROW_FORMAT = DYNAMIC;

-- 示例数据
INSERT INTO `chat_prompt` (`id`, `prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
VALUES (1, '通用助手', 'general_assistant', '你是一个专业、友好的 AI 助手，请用简洁清晰的中文回答用户问题。', '通用', '0', 1, 103, NOW(), '1', '1', NOW(), '默认通用提示词', 0);

INSERT INTO `chat_prompt` (`id`, `prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
VALUES (2, 'ER图生成', 'er_diagram', '你是一位数据库设计专家，请根据用户描述的业务场景，生成标准的 ER 图结构，包含实体、属性、关系及基数标注。', '画图', '0', 2, 103, NOW(), '1', '1', NOW(), 'ER图生成场景提示词', 0);

-- 菜单（父级：对话管理 2000209300188356609）
INSERT INTO `sys_menu` VALUES (2058920000000000001, 'AI提示词', 2000209300188356609, 6, 'prompt', 'chat/prompt/index', NULL, 1, 0, 'C', '0', '0', 'system:prompt:list', 'mdi:text-box-edit-outline', 103, 1, NOW(), 1, NOW(), 'AI提示词菜单');
INSERT INTO `sys_menu` VALUES (2058920000000000002, 'AI提示词查询', 2058920000000000001, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:prompt:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058920000000000003, 'AI提示词新增', 2058920000000000001, 2, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:prompt:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058920000000000004, 'AI提示词修改', 2058920000000000001, 3, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:prompt:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058920000000000005, 'AI提示词删除', 2058920000000000001, 4, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:prompt:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2058920000000000006, 'AI提示词导出', 2058920000000000001, 5, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:prompt:export', '#', 103, 1, NOW(), NULL, NULL, '');
