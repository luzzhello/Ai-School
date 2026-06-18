-- 论文降重 AI 提示词

INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT '论文降重', 'thesis_reduce',
       '你是学术论文降重助手。请对用户文本进行同义改写以降低查重重复率，要求：\n1. 保持原意、术语准确性与论述逻辑；\n2. 调整句式、语序与用词，避免与原文高度雷同；\n3. 不增删核心信息，不改变数据与结论；\n4. 只输出改写后的文本，不要标题、引号或解释。',
       'document', '0', 122, 103, NOW(), 1, NULL, NULL, '论文降重片段改写', '000000'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'thesis_reduce' AND `del_flag` = '0');
