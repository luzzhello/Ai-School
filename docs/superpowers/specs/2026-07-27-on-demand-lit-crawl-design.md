# 按需文献爬取（大纲生成并行）设计

> 日期：2026-07-27  
> 状态：待用户确认规格  
> 相关：`docs/superpowers/specs/2026-07-13-cnki-lit-crawler-design.md`、`tools/cnki-crawler`

## 1. 背景与问题

当前文献依赖 `tools/cnki-crawler` **离线广爬**关键词表导入 `lit_paper`，覆盖面广但：

- 与具体论文题目相关性弱；
- 维护成本高、易触发知网风控；
- 论文生成链路中「参考文献检索」只能搜本地已有库存。

目标：在用户**生成大纲**时，按题目拆词块 **按需爬取约 40～80 条**元数据入库，并在 UI 上用「大纲生成中 / 文献获取中」双状态提升体验；之后再打开文献弹窗检索勾选。

## 2. 目标与非目标

### 目标

- 流程 **B1**：填题目 → 点「生成大纲」→ 并行「大纲生成」+「文献按需爬取入库」→ 用户再查看/搜索/勾选文献。
- 拆 **3～5** 个词块，每词约 **20** 条，合计约 **40～80** 条。
- 爬取失败/超时 **不阻塞大纲**；文献步骤标失败或部分成功，仍可用库内已有数据检索。
- 默认 **无 Cookie** 访问列表与详情；Cookie 仅作可选兜底配置。
- 复用现有 Python 爬虫与 `lit_paper` 去重入库逻辑，Java 侧编排任务与进度。

### 非目标（本期不做）

- 不再以全量关键词表广爬作为主路径（工具可保留手工运维）。
- 不把知网解析整段重写为 Java。
- 不在「检索参考文献」按钮上同步阻塞爬取（避免检索变慢）。
- 不下载 PDF/CAJ 全文。

## 3. 用户流程与前端

### 3.1 步骤调整

| 原顺序（简化） | 新顺序 |
|----------------|--------|
| 题目 → 检索勾选文献 → 生成大纲 | 题目 → **生成大纲（并行文献获取）** → 打开文献查看/搜索/勾选 |

前置条件（截图等）仍按现有大纲接口约束；本期不改截图门槛，除非实现时发现强耦合再单列。

### 3.2 UI

- 「生成大纲」后展示双状态卡片（参考现有步骤条视觉）：
  - **大纲**：产出中 → 已完成 / 失败
  - **文献获取**：获取中 → 已完成（可带条数）/ 部分成功 / 失败
- 大纲完成后可进入大纲预览；文献未完成时「参考文献」入口显示进度或禁用，完成后可点开。
- 文献弹窗仍基于 `lit_paper` 检索 + 勾选确认（现有 SSE/confirm API 可复用，关键词默认题目）。

### 3.3 进度协议

推荐：**SSE 或短轮询** 推送任务状态（二选一在实现计划中定；优先与现有 paper SSE 风格一致）。

最小状态字段：

```text
taskId
outlineStatus: pending|running|done|failed
litStatus: pending|running|done|partial|failed
outlineError?, litError?
litFetchedCount?, litKeywordProgress?  // 可选
```

## 4. 后端编排

### 4.1 触发

用户调用「生成大纲」时（可扩展现有 `POST /api/paper/toc` 或新增 `POST /api/paper/toc-with-lit`）：

1. 校验 session（题目等）；
2. 异步启动文献任务（`sessionId` + `title`）；
3. 同步或并行生成 TOC（现有 `PaperTocService.generate`）；
4. 返回 TOC + `litTaskId`（若 TOC 同步返回）；文献进度走独立查询/SSE。

> 实现时若 TOC 很快、爬取很慢：TOC 先完成写 session；文献任务独立，不回滚 TOC。

### 4.2 题目拆词

输入：`session.title`。

规则（初版，可配置）：

1. 归一化空白、去书名号等噪声；
2. 去掉停用词（的、与、及、基于、面向、研究、设计、实现、系统、平台、分析…）；
3. 识别保留技术实体（大小写不敏感词表 + 连续英文/数字 token，如 SpringBoot、Vue、MySQL）；
4. 剩余中文按 2～4 字有意义片段或整词保留；
5. 去重后取 **3～5** 个作为 crawl keywords（不足则用整题截断兜底）。

输出示例：题目「基于 SpringBoot 的学生选课系统设计与实现」→ `SpringBoot`、`学生选课`、`选课系统` 等。

### 4.3 调用爬虫

- 入口：扩展 `tools/cnki-crawler` 为「单次任务」模式，例如：

  ```bash
  python -m src --config config.yaml crawl-task \
    --keywords SpringBoot,学生选课,选课系统 \
    --max-per-keyword 20 \
    --list-only false   # 或 true 若详情风控重，实现时按验证结果定默认
  ```

- Java 通过 **本地子进程** 或 **侧车 HTTP** 调用（实现计划选定；开发环境可先子进程）。
- 默认配置：**不强制 cookie**；`config.yaml` 中 cookie 可空或显式 `cookie: ""`。
- 限速沿用爬虫现有 delay；单次任务总超时建议可配（如 3～5 分钟），超时 → `litStatus=partial|failed`。

### 4.4 入库

- 爬虫写出 JSONL 后调用现有 `scripts/import_to_mysql.py` 逻辑，或 Java 读 JSONL/爬虫 stdout 后写入（优先复用 Python import 去重规则）。
- 去重顺序保持：`cnki_id` → `doi` → (`title_hash` + `year`)。
- `crawl_keyword` 记实际词块；`lit_source=CNKI`。

### 4.5 与参考文献确认

- 文献任务完成后，前端打开弹窗 → 现有 `POST /api/paper/references` 按题目/关键词搜库 → 用户勾选 → `confirm`。
- **不**在爬取阶段自动锁定 references；必须用户确认。

## 5. 失败与风控

| 情况 | 行为 |
|------|------|
| 大纲失败 | 大纲失败；文献任务可取消或继续（建议取消） |
| 文献验证码/非列表 | 任务 `failed` 或 `partial`；可选启用 Cookie 重试一次（配置开关，默认关） |
| 超时 | `partial`（已入库部分保留） |
| 拆词为空 | 用整题作唯一 keyword，仍限 20 条 |

合规：仅元数据；遵守限速；不下载全文。

## 6. 配置项（建议）

```yaml
paper:
  lit-ondemand:
    enabled: true
    max-keywords: 5
    min-keywords: 3
    max-per-keyword: 20
    task-timeout-sec: 300
    cookie-optional: true   # 默认不用；true 表示允许读爬虫 config cookie 兜底
    crawler-cmd: ...        # 或 base-url
```

## 7. 测试要点

- 拆词：典型毕设题目标题 → 3～5 词且含技术实体。
- 并行：TOC 完成而 lit 仍 running 时前端状态正确。
- 入库去重：同一题重复点生成大纲不产生大量重复行。
- 无 Cookie：list-only / 含详情 在目标环境可跑通（以实测为准；不通则文档记录改 Cookie 兜底）。
- 失败：模拟爬虫失败，大纲仍可用，文献可搜旧数据。

## 8. 里程碑

1. 爬虫 `crawl-task` CLI + 无 Cookie 默认验证  
2. Java 任务编排 + 进度 API/SSE  
3. 前端双状态 + 流程改为大纲优先再选文献  
4. 联调与限流参数落盘  

## 9. 开放问题（实现前可再定）

- TOC 与 lit 是否同一 HTTP 长连接 SSE，还是 TOC 同步 + lit 轮询。  
- 详情页是否默认 `list-only`（更快、更稳）再后台补全详情。  
- 子进程 vs 侧车 HTTP 的部署形态（本地 / paper.xunmaw.com）。
