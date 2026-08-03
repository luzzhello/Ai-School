-- 视觉识别：接入 Doubao-Seed-2.0-mini（火山方舟）
-- 官方 Model ID：doubao-seed-2-0-mini-260428（https://ai.volcengine.com/model）
-- 应用配置：chat.vision.default-model=Doubao-Seed-2.0-mini（与 chat.model.default-model 分离）
-- 调用入口：DrawChatModelSupport.buildVisionModel（remark 中 api_model:xxx 供方舟 API 使用）
-- 执行后请在管理后台「模型管理」将 api_key 改为火山方舟真实 API Key
-- 可重复执行（按 provider_code / model_name 幂等）

SET NAMES utf8mb4;

INSERT INTO `chat_provider`
(`id`, `provider_name`, `provider_code`, `provider_icon`, `provider_desc`, `api_host`,
 `status`, `sort_order`, `create_dept`, `create_time`, `create_by`, `update_by`, `update_time`,
 `remark`, `version`, `del_flag`, `update_ip`, `tenant_id`)
SELECT 20, '火山引擎', 'volcengine', NULL, '火山方舟大模型（OpenAI 兼容）',
       'https://ark.cn-beijing.volces.com/api/v3', '0', 9, 103, NOW(), '1', '1', NOW(),
       'Doubao / Seed 系列', NULL, '0', NULL, 0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `chat_provider` WHERE `provider_code` = 'volcengine'
);

INSERT INTO `chat_model`
(`id`, `category`, `model_name`, `provider_code`, `model_describe`, `model_dimension`,
 `model_show`, `api_host`, `api_key`, `create_dept`, `create_by`, `create_time`,
 `update_by`, `update_time`, `remark`, `tenant_id`)
SELECT 2049000000000000001, 'chat', 'Doubao-Seed-2.0-mini', 'volcengine',
       'Doubao-Seed-2.0-mini', NULL, 'N',
       'https://ark.cn-beijing.volces.com/api/v3', 'sk_xx',
       103, 1, NOW(), 1, NOW(),
       'api_model:doubao-seed-2-0-mini-260428',
       0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `chat_model` WHERE `model_name` = 'Doubao-Seed-2.0-mini'
);
