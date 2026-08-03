# 系统实现截图驱动大纲设计

**日期：** 2026-07-27  
**状态：** 已落地（实现完成，待环境验收）  
**范围：** Ai-School（后端）+ AiSchoolWeb（论文生成向导）  
**选定方案：** 方案 2 — 上传后识别预览，用户确认标题后再生成大纲；正文自动插图

---

## 1. 背景与目标

### 现状

- 第五章「系统实现」子目录由 SQL 表名规则推断（`PaperTocCustomizer.rebuildChapter5Modules`），生成 `5.1 管理员` / `5.2 用户` 下的功能小节。
- 正文 Prompt 会写 `【此处插入XXX功能界面截图】`，但**没有**上传与自动替换；侧栏仅标记「待手动插图」。

### 目标

1. 用户在**生成大纲前**上传系统功能截图（分管理员 / 用户两个上传区）。
2. AI（视觉模型）识别每张截图对应的功能名称。
3. 用户可编辑识别结果后确认；以此**完全替代** SQL 推第五章模块。
4. 后续生成该节正文时，**自动插入**对应截图。

### 非目标（本期不做）

- 不改第 4 章流程图 / E-R / 三线表等自动配图。
- 不用 SQL 再补「无图模块」小节。
- 不引入独立 OCR 引擎（复用现有 vision 多模态配置）。
- 不强制管理员与用户两侧都上传。

---

## 2. 已确认需求

| 项 | 选择 |
|----|------|
| 目录来源 | **A** 完全以截图为准 |
| 上传分区 | 上传区区分「管理员模块」「用户模块」两套图 |
| 子节形态 | 一张图 → 一节（如 5.1.1、5.2.1） |
| 生成大纲前置 | **B** 至少一个模块有图即可；无图的一侧不生成对应模块分支 |

---

## 3. 用户流程

```text
题目 / SQL / 代码
    ↓
系统功能截图
  ├─ 管理员功能：多图上传
  └─ 用户功能：多图上传
    ↓
「识别功能」→ 列表（缩略图 | 模块 | 可编辑标题 | 删）
    ↓
用户确认截图清单（可改标题、调序、删除）
    ↓
确认参考文献 → 生成大纲（ch5 仅来自截图清单）
    ↓
逐章生成正文 → 自动用绑定 assetUrl 替换截图占位
```

### 校验

- 两侧皆空 → 拦截生成大纲，提示至少一侧上传截图。
- 仅一侧有图 → 只生成该侧（`5.1` 或 `5.2`）+ `本章小结`。
- 识别未确认（无有效 `title`）→ 提示先识别并确认，或允许用默认「功能界面 N」但仍建议确认。

---

## 4. 数据模型

### 4.1 会话级截图清单

挂在 `PaperSession`（建议字段 `uiScreenshots`，持久化到 `paper_session` 新列 `ui_screenshots_json` TEXT/JSON）。

```json
[
  {
    "id": "uss_xxx",
    "module": "admin",
    "assetUrl": "/api/paper/assets/20260727/uuid.png",
    "title": "用户管理",
    "sort": 0,
    "confirmed": true
  }
]
```

| 字段 | 说明 |
|------|------|
| `id` | 稳定 id，生成 TOC 叶子节点时可编码进 `TocNode.id` 或平行映射表 |
| `module` | `admin` \| `user` |
| `assetUrl` | 复用 `POST /api/paper/upload-asset` 返回的相对 URL |
| `title` | AI 识别或用户编辑后的功能名（不含「5.1.1」前缀） |
| `sort` | 同模块内顺序 |
| `confirmed` | 用户是否在预览列表中确认过（可选；也可用「保存清单」表示确认） |

### 4.2 大纲节点扩展

在 `TocNode` 增加可选字段（序列化进 `toc_json`，无需新表）：

| 字段 | 说明 |
|------|------|
| `screenshotAssetUrl` | 叶子节绑定的截图 URL；无图节（如本章小结）为空 |

生成 ch5 时写入；正文插图与侧栏状态依赖此字段。

---

## 5. API 设计

基路径：`/api/paper`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/upload-asset` | **复用**；上传单张截图 |
| POST | `/screenshots/analyze` | Body: `{ sessionId, items?: [{ id, module, assetUrl }] }`；缺省则分析会话内全部待识别项。返回带 `title` 的列表 |
| PUT | `/screenshots` | Body: `{ sessionId, screenshots: [...] }`；全量覆盖保存（含标题、排序、删除后的清单） |
| GET | `/session/{id}` | 已有；响应中带上 `uiScreenshots` |
| POST | `/toc` | 行为变更：`rebuildChapter5` **优先/仅**读 `uiScreenshots`；无图则失败或明确错误码 |
| POST | `/toc/refresh-ch5` | 同样改为截图驱动；无截图时返回业务错误，不再回退 SQL |

视觉模型：复用 `chat.diagram.vision-model`（`DrawDiagramProperties.visionModel`），未配置则回退默认聊天模型；参考 `DrawReferenceImageLoader` 将 asset 转 base64 多模态输入。

计费：与现有章节/画图视觉调用策略对齐（若项目对 vision 有单独 feature code 则复用；否则本期可先不计费或记入论文生成相关额度——实现时与 `IFeatureCoinService` 现有约定对齐，**不得静默免计费除非产品确认**）。默认建议：识别按次数或按图张数记一次小额消耗，需在实现计划中与产品再确认；设计文档默认「实现时对齐现有 paper 相关计费，无则先打日志不阻断」。

---

## 6. 第五章 TOC 形态

```text
5 系统实现
  5.1 管理员功能模块          ← 仅当存在 module=admin 的截图
    5.1.1 {title}
    5.1.2 {title}
    …
  5.2 用户功能模块            ← 仅当存在 module=user 的截图
    5.2.1 {title}
    …
  5.x 本章小结                ← 保留（编号随上面模块数量调整，或固定为末节）
```

- 标题展示建议：`{编号} {title}功能`（管理端可带「管理」后缀策略与现网文案对齐，如「用户管理功能」；识别结果已是完整名则不再重复拼接——实现时：若 title 已含「功能/管理」则原样加编号）。
- `PaperTocCustomizer.rebuildChapter5Modules`：有 `uiScreenshots` 走新逻辑；**删除/停用** SQL `PaperBusinessModuleResolver` 对 ch5 的路径（SQL 仍服务 ch3/ch4 等）。

---

## 7. 正文自动插图

1. `PaperChapterPrompts.promptChapter5Section` 继续要求输出 `【此处插入{模块名}功能界面截图】`（或根据 title 生成一致占位文案）。
2. 章节生成完成（SSE `done`）后，前端在 `generateSingleChapter` 中仿 `injectFuncFlowIntoContent`：
   - 读取当前 `TocNode.screenshotAssetUrl`；
   - 用 `![图题](assetUrl)` 替换 UI 截图占位（复用/扩展 `paperUiScreenshotPlaceholder.ts`）。
3. 侧栏：`hasPendingUiScreenshotPlaceholder` 为 false 且已有 Markdown 图则不显示黄图「待插」；有 `screenshotAssetUrl` 且已注入可显示已配图状态。

可选加固（后端）：`finalizeChapter` 若节点带 `screenshotAssetUrl` 且正文仍含占位，则服务端直接替换，避免前端漏跑。

---

## 8. 前端改动要点（AiSchoolWeb）

- `PaperGenerator.vue` 配置区：新增双区上传 + 识别结果表（标题可编辑）。
- API 封装：`analyzePaperScreenshots` / `savePaperScreenshots`。
- 生成大纲前置：`ensureScreenshotPrerequisites`（至少一侧有图且已保存有效 title）。
- `generateSingleChapter` 末尾：注入 UI 截图。
- SQL 变更后 **不再**静默 `refresh-ch5` 覆盖用户截图目录；若仍调用 refresh，须改为「仅按已保存截图重建编号」。

---

## 9. 错误与边界

| 场景 | 处理 |
|------|------|
| 图片无法识别 | 该项 title 置空或「未识别功能」，前端标红，要求用户手填后才能确认 |
| 单图过大 | 沿用 upload-asset 5MB 限制 |
| 会话过期 | 图文件仍在磁盘；清单丢失需重传（可后续做按 sessionId 目录隔离） |
| 用户改 TOC 标题 | 不自动改 `uiScreenshots.title`；插图仍靠 `screenshotAssetUrl` |
| 删除已生成章节的图 | 本期不级联清正文；重新生成该节即可 |

---

## 10. 实现分期建议

1. **P0**：数据模型 + 上传保存 + 截图驱动 rebuild ch5 + 生成后插图  
2. **P1**：视觉识别 API + 预览编辑 UI  
3. **P2**：后端 finalize 兜底插图、侧栏状态细化、计费对齐  

P0+P1 可同迭代交付；无视觉模型时可先手填标题冒烟。

---

## 11. 验收标准

- [ ] 仅管理员有图时，大纲只有 `5.1` + 本章小结，无 `5.2`
- [ ] 仅用户有图时，对称成立
- [ ] 两侧皆空无法生成大纲
- [ ] 识别后可改标题，改后的标题出现在 TOC
- [ ] 生成某叶子节正文后，该节含对应截图 Markdown，占位消失
- [ ] SQL 再解析不再把 ch5 改回表名模块列表

---

## 12. 自检记录

- 无「TBD/XXX」占位未决项（计费细节实现时对齐现网，已标明）。
- 与「完全截图驱动 / 分区上传 / 至少一侧」需求一致，无 SQL 回退矛盾。
- 范围已排除第 4 章配图与强制双侧上传。
