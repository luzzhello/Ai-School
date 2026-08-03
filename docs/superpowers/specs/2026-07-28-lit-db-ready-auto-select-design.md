# 大纲生成时库内文献充足则跳过爬取并自动选用

日期：2026-07-28  
状态：已实现（2026-07-28）  
相关：`2026-07-27-on-demand-lit-crawl-design.md`（按需爬取）

## 背景

第一次生成大纲时，前端会并行调用 `POST /api/paper/lit-ondemand/start` 触发知网中英双语爬取。若库内已有足够相关文献，爬取浪费时间且用户仍需手动勾选。

目标：生成大纲启动文献任务时，先按参考文献弹窗同一套检索判断库存；中英各 ≥50 则跳过爬取，并自动确认写入会话参考文献（中文 18 + 英文 2）。

## 决策摘要

| 项 | 选择 |
|---|---|
| 入口 | 扩写现有 `lit-ondemand/start`（方案 A），不新增独立接口 |
| 检索规则 | 与参考文献弹窗一致：`LitPaperSearchService` + `LitQueryNormalizer`，题目整句清洗后一次查询 |
| 充足阈值 | 中文 ≥50 **且** 英文 ≥50（`limit=50` 的返回条数） |
| 自动选用 | 检索结果顺序取前 18 中文 + 前 2 英文，**自动 confirm 落库** |
| 不足时 | 维持现有中英双语爬取：只入库、不自动勾选 |
| 已有勾选 | 不覆盖 `session.references`；仅在参考文献为空时自动写入 |

## 流程

```text
generateOutline
  └─ POST /lit-ondemand/start
       ├─ search(title, zh, 50) + search(title, en, 50)
       ├─ 两者 size >= 50 且 session.references 为空
       │    → 取 zh[:18] + en[:2]
       │    → 写 references + status=ref_confirmed + syncReferenceChapter
       │    → task: litStatus=done, source=db, fetchedCount=20 (18+2)
       └─ 否则
            → 现有 bilingual crawl → import lit_paper / lit_paper_en
            → task: source=crawl（不自动勾选）
```

检索排序沿用现有：`cite_count DESC, year DESC`，不做额外重排。

## API / 状态字段

`LitOnDemandStatusVo`（及前端 `LitOnDemandStatus`）增补：

| 字段 | 说明 |
|---|---|
| `source` | `db` = 库内直选；`crawl` = 爬取路径 |
| `selectedCountZh` / `selectedCountEn` | 库内直选实际写入篇数；爬取路径为 0 |
| `fetchedCount` / `Zh` / `En` | 库内直选时复用为 20 / 18 / 2，便于前端展示；爬取路径仍表示新入库条数 |

接口路径不变；旧前端忽略新字段仍可工作。

可选：库内直选成功时，status 响应可附带已选 `references` 列表，避免前端再调会话详情；若实现成本高，前端在 `source=db` 时拉一次 session 同步 `aiRefList` 亦可。

## 配置

挂在 `paper.lit.ondemand`：

```yaml
db-ready-min-count: 50
auto-select-zh: 18
auto-select-en: 2
```

## 后端实现要点

- 主要改动：`LitOnDemandService`；注入 `LitPaperSearchService`、复用 `PaperSessionStore` + `PaperReferenceContentHelper`
- 写参考文献逻辑与 `PaperController.confirmReferences` 对齐（setReferences、status、syncReferenceChapter、持久化）
- 已有非空 references：跳过自动写入；是否仍爬取：库存不足则仍爬补库，库存充足则可不爬
- 不改 `LitQueryNormalizer` / 检索排序 / 爬虫子进程协议

## 前端实现要点

- `LitOnDemandStatus` 增加 `source`、`selectedCountZh/En`
- `applyLitStatus`：`done && source==='db'` 提示「库内文献充足，已自动选用中文 18 + 英文 2…」，并同步本地 `aiRefList`
- `source==='crawl'`：保持现有入库条数文案，不自动勾选

## 非目标

- 不改参考文献弹窗手动检索/勾选/自动补全至 20 的现有 UX
- 爬取成功后仍不自动勾选
- 不统一 `TitleKeywordSplitter` 与 `LitQueryNormalizer`（本需求明确用后者）

## 测试

- 单测：充足 → 不调爬虫客户端、写入 18+2、`source=db`
- 单测：任一侧 <50 → 调爬虫、`source=crawl`、不写 references
- 单测：session 已有 references → 不覆盖
- 手工：大纲完成后卡片提示 + 参考文献列表已有 20 篇，可直接生成正文

## 开放细节（实现时默认）

- 英文检索在题目含中文时走 `title_zh` 等中译字段（现有 `LitPaperSearchService` 行为），不另开分支
- 自动写入的 `Reference.language` 分别标 `zh` / `en`，`index` 1..20 重排
