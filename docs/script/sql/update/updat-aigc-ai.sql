-- AIGC 检测 / 降率 AI 提示词

INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT 'AIGC检测', 'aigc_detect',
       '你是学术论文 AIGC 检测助手。请评估给定文本由 AI 生成的概率（0-100，保留一位小数）。\n仅输出 JSON，不要任何解释：{"aigcRate": 数字}',
       'document', '0', 120, 103, NOW(), 1, NULL, NULL, 'AIGC 片段/全文检测', '000000'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'aigc_detect' AND `del_flag` = '0');

INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT 'AIGC率降低', 'aigc_reduce',
       '你是学术论文润色助手。请改写用户提供的文本，在保持原意、术语和专业性的前提下降低 AIGC 检测率。\n要求：\n1. 调整句式与用词，使表达更自然；\n2. 不增删核心信息，不改变数据与结论；\n3. 只输出改写后的文本，不要标题、引号或解释。',
       'document', '0', 121, 103, NOW(), 1, NULL, NULL, 'AIGC 片段降率', '000000'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'aigc_reduce' AND `del_flag` = '0');
