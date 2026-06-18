-- 非 AI / SQL 模式功能定价（默认 0 金币，可在后台「功能定价」调整）

INSERT INTO `uc_feature_price` (`feature_code`, `feature_name`, `category`, `price_type`, `price_coins`, `status`, `sort_order`, `remark`, `create_time`)
SELECT * FROM (
  SELECT 'er_sql' AS feature_code, 'ER图 SQL解析' AS feature_name, 'draw' AS category, 'FIXED' AS price_type, 0 AS price_coins, '0' AS status, 11 AS sort_order, '在线画图' AS remark, NOW() AS create_time
  UNION ALL SELECT 'sql_three_line_sql', 'SQL三线表 SQL导出', 'document', 'FIXED', 0, '0', 61, '文档相关', NOW()
  UNION ALL SELECT 'use_case_spec_manual', '用例说明 手动导出', 'document', 'FIXED', 0, '0', 71, '文档相关', NOW()
  UNION ALL SELECT 'func_test_manual', '功能测试 手动导出', 'document', 'FIXED', 0, '0', 81, '文档相关', NOW()
  UNION ALL SELECT 'word_table_manual', 'Word表格 手动导出', 'document', 'FIXED', 0, '0', 91, '文档相关', NOW()
  UNION ALL SELECT 'course_code_ai', '课设代码 AI生成', 'document', 'FIXED', 500, '0', 130, '文档相关', NOW()
  UNION ALL SELECT 'course_code_sql', '课设代码 SQL生成', 'document', 'FIXED', 300, '0', 131, '文档相关', NOW()
) t
WHERE NOT EXISTS (SELECT 1 FROM `uc_feature_price` p WHERE p.feature_code = t.feature_code);
