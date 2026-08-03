# nature-skills → 论文 Agent 迁移方案

> 日期：2026-07-24  
> 范围：抽取规则改造 `PaperWritingStandards` / `PaperChapterPrompts` 等，不整仓接入 Claude Code Skill。

## 取舍摘要

| 优先迁入 | 可选 | 不迁 |
|---|---|---|
| shared、writing、polishing、citation、ref-verifier、paper2ppt | reader、reviewer、literature-pipeline（弱）、figure（弱） | response、data、statistics、downloader、patent、proposal（暂） |

## 分期

- **S1（已落地代码）**：WP1 底座规范 + WP2 摘要/绪论/相关技术/总结 Prompt
- **S2（已落地代码）**：需求/设计/实现/测试分章碎片 + 引用角标后质检
- **S3（已落地代码）**：答辩 PPT（paper2ppt）导出
- **S4**：评阅检查单等增值

## S1 代码落点

- `PaperWritingStandards.java`：主张–证据–边界、失败模式优先级、术语一致性、扩禁用套话
- `PaperChapterPrompts.java`：摘要、1.1/1.2.3/1.3、第二章技术节、第七章总结

## S2 代码落点

- `PaperChapterPrompts.java`：第三～六章结构碎片（主张→证据→边界；禁跨章复述）
- `PaperCitationSanitizer.java` + `PaperGenerateService.finalizeChapter`：正文 `[n]` 仅保留已确认参考文献序号

## S3 代码落点

- 后端：`GET /api/paper/export-ppt/{sessionId}`、`PaperDefensePptService`（按大纲拆页 + 章节要点 + 备注话术）
- 前端：`downloadPaperPpt` + 论文页「导出答辩PPT」

## 原则

线上不依赖 Claude Code 读 `SKILL.md`；只迁与中文计算机毕设兼容的结构与质检规则。
