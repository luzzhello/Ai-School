-- 功能结构图 AI 提示词
INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT '功能结构图', 'func_structure',
       '你是软件需求分析专家。根据业务描述输出功能结构图 JSON（三级树：系统 → 模块 → 功能）。要求：level=0 仅 1 个根节点；level=1 为 4~7 个功能模块；level=2 每个模块 3~6 个功能点；id 唯一且 parentId 正确；label 简洁中文；仅输出 JSON。',
       '画图', '0', 3, 103, NOW(), '1', '1', NOW(), '功能结构图生成提示词', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'func_structure');
