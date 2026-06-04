-- 功能测试文档：AI 提示词
INSERT INTO `chat_prompt` (`prompt_name`, `prompt_code`, `prompt_content`, `category`, `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT '功能测试文档', 'func_test_doc',
       '你是软件功能测试专家。根据用户描述的系统/模块测试需求，输出标准功能测试用例表 JSON。

要求：
1. documentTitle：文档主题，如「管理员功能测试」
2. testCases：数组，每条包含 caseId（如 GA001）、caseName、preconditions、testSteps、expectedResult、testResult（填「成功」）
3. 测试步骤写清操作顺序；预期结果与步骤对应
4. 至少输出 3 条有代表性的测试用例
5. 只输出 JSON，不要 markdown，不要解释',
       '文档', '0', 14, 103, NOW(), '1', '1', NOW(), '功能测试文档 AI 生成', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `chat_prompt` WHERE `prompt_code` = 'func_test_doc' AND `del_flag` = '0');
