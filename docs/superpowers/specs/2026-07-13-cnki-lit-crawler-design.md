# 知网软件工程文献爬虫与文献库设计

**日期**：2026-07-13  
**状态**：已定稿，待实现  
**范围**：独立爬虫工具 + MySQL 文献库 + 论文生成检索替换 LLM 编造

## 1. 背景与目标

当前论文生成「AI 提供文献」由 `PaperReferenceService` 调用 LLM 生成 JSON，文献真实性无法保证。用户可手动粘贴知网「查新（引文格式）」，但无法规模化。

**目标**：

1. 提供独立爬虫，从中国知网采集**软件工程类**论文**元数据**（不存全文 PDF/CAJ）。
2. 将数据导入 MySQL 文献库（目标规模 10 万+）。
3. 论文生成「AI 提供文献」**完全改为库检索**，禁止回退 LLM 编造。
4. 保留用户自定义粘贴知网查新路径。

**约束**：

- 使用个人知网账号（Cookie），必须限速、断点续爬，避免封号。
- 第一期检索用 MySQL FULLTEXT；表结构预留后续 ES/向量增强。
- 爬虫不运行在 ruoyi 业务进程内。

## 2. 方案选型

采用 **方案 1：Python 独立爬虫 + MySQL 文献库 + 替换论文检索**。

| 方案 | 结论 |
|------|------|
| 1. Python 工具 + 导入 + Java 检索改造 | **采用** |
| 2. 封装开源 CNKI CLI | 字段与限速不可控，不采用为主路径 |
| 3. Java 后端内爬虫 | 长任务/反爬不适合业务服务，不采用 |

## 3. 整体架构与数据流

```text
[知网 kns]
    ↑ Cookie + 限速
[tools/cnki-crawler]  Python 独立工具
    → 按软工关键词检索列表
    → 拉详情元数据（不含 PDF）
    → 断点状态 + JSONL 落盘
         ↓
[import_to_mysql]  去重导入
         ↓
[MySQL lit_paper (+ lit_paper_ref)]
         ↓
[PaperReferenceService]  按题目/关键词检索
         ↓
前端「AI 提供文献」勾选 → confirm → 现有 paper_reference 会话表
```

**边界**：

- 爬虫：只采集与导出，不嵌入 ruoyi 进程。
- 导入：可重复执行，幂等去重。
- 论文生成：只读文献库；确认后仍写入会话级 `paper_reference`；写作/导出主流程不变。
- 自定义粘贴知网查新：保留。

**第一期不做**：Elasticsearch/向量、全文 PDF、管理后台 UI、实时在线爬知网、验证码破解、多账号池。

## 4. 数据模型

### 4.1 主表 `lit_paper`

| 字段 | 说明 |
|------|------|
| `id` | 自增主键 |
| `cnki_id` | 知网文献 ID（有则唯一） |
| `doi` | DOI（有则唯一索引；空值允许多条） |
| `title` | 标题 |
| `authors` | 作者（原文串） |
| `organs` | 机构 |
| `abstract_text` | 摘要 |
| `keywords` | 关键词 |
| `source` | 期刊/会议名 |
| `year` | 年份 |
| `volume` / `issue` / `pages` | 卷、期、起-止页码 |
| `publisher` / `publish_place` / `translator` | 出版者、出版地、译者 |
| `degree` / `degree_place` | 学位论文类型与授予单位所在地 |
| `patent_country` / `patent_kind` / `patent_no` | 专利国名、种类、专利号 |
| `standard_code` / `publish_date` | 标准代号、专利/标准出版日期 |
| `doc_type` | J/D/C/M/P/S |
| `cite_count` | 被引次数 |
| `lit_source` | 固定如 `CNKI` |
| `citation_gbt` | GB/T 7714 引用格式 |
| `detail_url` | 详情页 URL（可选） |
| `title_hash` | 规范化标题哈希（去重兜底） |
| `crawl_keyword` | 首次命中的检索词 |
| `crawled_at` / `created_at` / `updated_at` | 时间戳 |
| `status` | `active` / `incomplete` |

本期不加 embedding 列；若后续做向量检索，使用旁表。

### 4.2 附表 `lit_paper_ref`

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `paper_id` | → `lit_paper.id` |
| `ref_index` | 序号 |
| `raw_text` | 参考文献原文 |

参考文献能抓到则存；抓不到不阻断主记录入库。

### 4.3 去重规则（导入幂等）

1. `cnki_id` 相同 → 更新  
2. 否则 `doi` 相同 → 更新  
3. 否则 `title_hash` + `year` 相同 → 更新  
4. 否则插入  

### 4.4 索引

- `FULLTEXT(title, keywords, abstract_text)`（MySQL ngram）
- 普通索引：`year`, `cite_count`, `doc_type`, `title_hash`

### 4.5 与会话表关系

`paper_reference` **不改结构**。从 `lit_paper` 查出后映射到现有 `Reference` DTO（`citation` ← `citation_gbt`）。

## 5. 爬虫工具设计

### 5.1 目录结构

```text
Ai-School/tools/cnki-crawler/
  README.md
  requirements.txt
  config.example.yaml
  keywords/se.txt
  src/
    crawl_search.py
    crawl_detail.py
    parse.py
    gbt7714.py
    checkpoint.py
    export_jsonl.py
  scripts/
    import_to_mysql.py
  data/                    # gitignore
```

### 5.2 采集流程

1. 读取配置中的个人 Cookie 与限速参数。
2. 按 `keywords/se.txt` 逐词检索（主题/关键词；文献类型可配：期刊、会议、硕博）。
3. 列表页采集：标题、作者、来源、年份、被引、详情 URL、cnki_id。
4. 详情页补全：机构、摘要、关键词、DOI、参考文献、官方引用格式（有则优先）。
5. 每条追加写入 `data/papers.jsonl`；checkpoint 记录「关键词 + 页码 + 已抓 URL」。

### 5.3 限速与风控

- 列表/详情请求间隔可配；默认详情间隔 ≥ 3–5s，带抖动。
- 单日详情上限可配。
- 并发默认 1。
- 遇验证码或登录失效：停止爬取，保留 checkpoint，人工更新 Cookie 后续跑。

### 5.4 输出

JSONL 一行一篇，字段与 `lit_paper` 对齐；`references` 为字符串数组。

缺官方 GB/T 7714 时，由 `gbt7714.py` 按已有字段本地拼接。

### 5.5 明确不做

下载 PDF/CAJ、自动打验证码、多账号轮询。

## 6. 论文生成检索改造

### 6.1 行为变更

`PaperReferenceService.generate` **不再调用 ChatModel**。

1. 检索词：请求 `keyword`，空则用论文题目。
2. 查询 `lit_paper`（`status=active`）：FULLTEXT 匹配 + 近 N 年过滤（可配，默认约 5–8 年）+ 排序（相关度 → `cite_count` → `year`）。
3. `language`：`zh` / `en` / 混合均**只返回库内真实条目**；不足时 SSE 提示数量，**禁止 LLM 补编**。
4. 映射为现有 `Reference`，SSE 仍逐条 `type: reference` 推送。
5. `confirm`、写作注入、`paper_reference` 持久化不变。

### 6.2 接口兼容

路径仍为 `/api/paper/references`；`model` 参数可忽略。前端弹窗可后续改文案为「从文献库检索」。

### 6.3 英文文献

第一期爬虫主攻知网中文软工；英文不足时依赖「自定义粘贴」，或二期接入其他数据源。

## 7. 错误处理

| 场景 | 处理 |
|------|------|
| Cookie 失效 / 验证码 | 停爬，保留 checkpoint，日志提示更新 Cookie |
| 详情缺字段 | 主记录入库，`status=incomplete` |
| 参考文献缺失 | 不阻断主记录 |
| 导入冲突 | 按去重规则更新 |
| 论文检索 0 命中 | SSE 可读错误，不编造 |
| 限速 / 429 | 指数退避，仍失败则停爬 |

## 8. 测试计划

- 解析器：列表/详情 HTML fixture 单元测试。
- 去重：同 cnki_id / doi / title_hash 导入幂等。
- 检索：预置 `lit_paper` 数据，断言接口只返回库内数据且无 LLM 调用。
- 手工：小关键词试爬 → 导入 → 论文页勾选确认。

## 9. 第一期交付清单

1. Python 爬虫（Cookie、限速、断点、JSONL）。
2. MySQL `lit_paper` / `lit_paper_ref` 建表 SQL + 导入脚本。
3. `PaperReferenceService` 改为库检索。
4. README（Cookie 配置、合规与限速说明）。

## 10. 合规说明

- 仅采集与存储公开元数据及用户有权访问的详情字段，不存储全文文件。
- 使用须遵守知网服务条款与账号使用规范；个人账号务必限速。
- 文献库仅供本产品论文生成检索使用，不做对外转售或全文分发。

## 11. 二期预留

- Elasticsearch 或向量语义检索。
- 英文文献源（如 Crossref）。
- 管理后台浏览/审核文献。
- 管理端批量触发爬取任务（仍建议独立 worker，不塞进 Web 请求线程）。
