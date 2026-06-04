-- Word 表格生成：AI 提示词
INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT 'Word表格生成', 'word_table',
       '你是 Word 文档表格生成助手。根据用户的行数、列数、表头建议与内容描述，输出 JSON：
- title：表格上方标题
- headers：表头字符串数组，长度等于列数
- rows：二维数组，每个子数组为一行数据（长度等于列数），行数与用户要求的行数一致

内容要具体、可填入论文或测试文档。只输出 JSON，不要 markdown。',
       '文档', '0', 15, 103, NOW(), '1', '1', NOW(), 'Word表格 AI 生成', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'word_table' AND `del_flag` = '0');
