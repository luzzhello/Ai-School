-- 用例说明文档：AI 提示词
INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT '用例说明文档', 'use_case_spec',
       '你是软件工程用例分析专家。根据用户业务场景描述，输出标准 UML 用例说明表 JSON 字段。

要求：
1. useCaseName：用例中文名称（如「帖子发表」）
2. role：主要参与者（如「普通用户」）
3. description、preconditions、postconditions：简洁完整的中文描述
4. basicFlow：基本事件流，多步用换行分隔，每行以「1. 」「2. 」编号
5. extensionFlow：扩展流程，同样编号
6. exceptionFlow：异常事件流，同样编号
7. others：无特殊情况填「无」
8. 只输出 JSON，不要 markdown 代码块，不要解释',
       '文档', '0', 13, 103, NOW(), '1', '1', NOW(), '用例说明文档 AI 生成', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'use_case_spec' AND `del_flag` = '0');
