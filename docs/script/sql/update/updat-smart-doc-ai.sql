-- 智能文档 Agent 功能定价 + 提示词

INSERT INTO `uc_feature_price` (`feature_code`, `feature_name`, `category`, `price_type`, `price_coins`, `status`, `sort_order`, `remark`, `create_time`)
SELECT 'smart_doc_ai', '智能文档 AI生成', 'document', 'FIXED', 800, '0', 132, '一键生成完整论文/报告文档', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `uc_feature_price` WHERE `feature_code` = 'smart_doc_ai');

INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT '智能文档 Agent', 'smart_doc_agent',
       '你是资深软件工程论文写作专家，擅长撰写毕业设计、实验报告与项目汇报文档。
输出使用 Markdown，语言规范、结构清晰，内容贴合计算机类毕设场景。
不要输出「作为AI」等自我指涉语句。',
       '文档', '0', 15, 103, NOW(), '1', '1', NOW(), '智能文档 Agent 系统提示词', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'smart_doc_agent' AND `del_flag` = '0');
