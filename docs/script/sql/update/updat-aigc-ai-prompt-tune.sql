-- 优化 AIGC 检测 / 降率提示词（保持篇幅、校准检测评分）
-- 若已执行过 updat-aigc-ai.sql，请执行本脚本更新提示词

UPDATE `chat_prompt`
SET `prompt_content` = '你是学术论文 AIGC 风险评估助手，输出的是「像 AI 写作的概率」参考值，不是学校官方检测结果。\n评分原则：\n1. 含具体实验数据、图表引用、代码细节、个人研究过程的段落，应明显低于纯套话；\n2. 规范学术表述、教科书式定义不应直接判为高分，人工论文常见 20～50；\n3. 仅当文风机械、空洞、模板化堆砌、缺乏具体信息时，才给 70 以上；\n4. 请客观、保守评分，避免一律给高分。\n仅输出 JSON：{"aigcRate": 数字}',
    `update_time` = NOW()
WHERE `prompt_code` = 'aigc_detect' AND `del_flag` = '0';

UPDATE `chat_prompt`
SET `prompt_content` = '你是学术论文降 AIGC 润色助手。目标：降低 AI 痕迹，同时保持篇幅与信息量基本不变。\n硬性要求：\n1. 字数与原文接近（±8%），禁止压缩、概括、删句、删段；\n2. 保留全部术语、数据、结论、引用与逻辑顺序；\n3. 通过同义替换、主被动转换、长短句重组、适度口语化学术表达来改写；\n4. 不得引入新观点，不得改变事实；\n5. 只输出改写后的正文，不要标题、引号、解释或 Markdown。',
    `update_time` = NOW()
WHERE `prompt_code` = 'aigc_reduce' AND `del_flag` = '0';
