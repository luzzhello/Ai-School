# 会话级自定义论文排版模板设计

**日期**：2026-07-22  
**状态**：已定稿，待实现  
**范围**：用户在当前论文会话上传本校 docx + 版式参数；与学校模板互斥；导出按会话自定义优先  
**前置**：`2026-07-21-paper-format-template-design.md`（学校模板 + 会话微调）已落地

## 1. 背景与目标

### 现状

- 管理端维护全局 `paper_format_template`（docx + format_json）
- 用户端仅能选择启用中的学校模板，并微调子集字段写入 `format_override_json`
- 用户端**不能**上传本校 docx

### 目标

1. 用户可为**当前会话**上传本校论文排版模板：`docx` + 版式参数（`PaperFormatConfig`）
2. 可选是否「强制用版式参数覆盖模板样式」（`patchTemplateStyles`）
3. 与学校模板下拉互斥：上传后进入自定义模式；改选学校模板需确认并丢弃自定义 docx
4. 导出优先使用会话自定义 docx 与合并后的生效配置

### 非目标

- 「我的模板」跨会话复用 / 用户级默认偏好表
- 上传后申请进入全站模板库（管理审核）
- 从 docx 自动识别字体填入 format
- 历史会话批量迁移

## 2. 方案选型

采用 **方案 1：会话字段扩展**。

| 方案 | 结论 |
|------|------|
| 1. `paper_session` 增加自定义 docx / format / patch 字段 | **采用**：符合「仅本会话」、不污染全局模板表 |
| 2. 临时 `paper_format_template` 行（owner + session） | 拒绝：归属校验与清理成本高 |
| 3. 路径塞进 `format_override_json` | 拒绝：JSON 职责混乱，导出难校验 |

## 3. 数据模型

### 3.1 `paper_session` 新增列

| 列 | 类型 | 说明 |
|----|------|------|
| `custom_format_docx_path` | varchar(500) NULL | 相对 upload 路径；非空 = 自定义模式 |
| `custom_format_docx_name` | varchar(255) NULL | 原始文件名（UI 展示） |
| `custom_format_docx_size` | bigint NULL | 字节数 |
| `custom_format_json` | mediumtext NULL | 上传时提交的版式主配置（可为完整或相对 Default 的稀疏 JSON） |
| `custom_patch_styles` | tinyint NULL | `1` 强制 patch；`0` 不 patch；自定义模式默认 `1` |

已有列保持不变：

- `format_template_id`：学校模板；自定义模式下置 `NULL`
- `format_override_json`：微调差异；自定义与学校模式均可用

### 3.2 文件落盘

```text
{upload}/paper/session-format/{userId}/{sessionId}/thesis-template.docx
```

- 再次上传：覆盖同路径并更新元数据
- 清除自定义 / 切到学校模板：删除该会话目录（删文件失败仍清空 DB 字段并打日志）

### 3.3 迁移

新增 SQL：`docs/script/sql/update/updat-paper-session-custom-format.sql`  
仅 `ALTER TABLE paper_session ...`；`paper_session` 已在 `tenant.excludes`。

## 4. 生效与合并规则

```text
若 custom_format_docx_path 非空（自定义模式）：
  docx = 会话自定义文件
  EffectiveFormat = deepMerge(DefaultFormat, custom_format_json, format_override_json)
  patchTemplateStyles = (custom_patch_styles == 1)
否则（学校模式）：
  沿用既有逻辑：
  docx = format_template_id 对应模板（或默认模板 / 内置 fallback）
  EffectiveFormat = deepMerge(DefaultFormat, template.format_json, format_override_json)
  patchTemplateStyles = EffectiveFormat.export.patchTemplateStyles（既有）
```

合并约定与一期相同：`null`/缺省不覆盖上层；非法值拒绝保存；导出前再校验。

### 4.1 互斥

| 操作 | 行为 |
|------|------|
| 上传自定义成功 | 写入 `custom_*`；`format_template_id = NULL`；可选 `clearOverride`（默认不清，避免丢掉已调微调；上传弹层可带「用当前 effective 预填 custom_format_json」） |
| 下拉选择学校模板 | UI 二次确认「将丢弃已上传本校模板」→ 删自定义文件与 `custom_*` → 绑定 `format_template_id`，建议 `clearOverride: true`（与现切换模板一致） |
| 清除自定义 | 删文件与 `custom_*`；不自动绑定学校模板（`format_template_id` 保持 NULL → 走默认模板） |
| 「恢复模板默认」reset | **仅**清空 `format_override_json`；**不删**自定义 docx / `custom_format_json` |

## 5. API

均需登录，并校验会话归属当前用户。路径前缀与现网一致：`/api/paper`。

### 5.1 新增

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/session/{sessionId}/format/custom-docx` | `multipart/form-data`：`file`（必填）；`format`（可选 JSON 字符串或 part）；`patchStyles`（可选，默认 true） |
| DELETE | `/session/{sessionId}/format/custom-docx` | 清除自定义 docx 与 `custom_*` |

请求约束：

- 扩展名 `.docx`；Content-Type 校验放宽但以扩展名+ZIP 魔数为准
- 大小上限 **10MB**
- `format` 若提供，按 `PaperFormatConfig` 校验（页边距 5–50 mm、字号 6–72 pt 等，同现）
- `format` **省略时**：将上传前服务端算出的当前 `EffectiveFormat` 快照写入 `custom_format_json`（前端弹层默认也会带上预填值；服务端兜底避免空配置）
- `patchStyles` 省略时默认 `true`；一期不单独提供「只改开关不换文件」的 API，改开关需在更换/重传弹层提交

### 5.2 扩展

| 方法 | 路径 | 变更 |
|------|------|------|
| GET | `/session/{sessionId}/format` | 响应增加：`mode: "school" \| "custom"`；`customDocxName`；`customPatchStyles`；`hasCustomDocx`；`customFormat`（解析后的对象，可无） |
| PUT | `/session/{sessionId}/format` | 若当前为 custom 且 body 带非空 `templateId`：先执行清除自定义，再绑定学校模板（与 UI 确认配合） |
| POST | `/session/{sessionId}/format/reset` | 行为不变：只清 override |
| GET | `/export/{sessionId}` | 路径不变；服务端按 §4 选 docx 与 patch |

不新增管理端权限；不改 `/format-template/*` 管理 API。

## 6. 导出改造（`WordExportService`）

在现有 `resolveEffective` / 打开模板路径上增加分支：

1. 若会话有 `custom_format_docx_path`：打开该文件；若磁盘缺失 → 明确错误「自定义排版模板文件缺失，请重新上传」
2. EffectiveFormat 按 §4 合并
3. `patchTemplateStyles`：自定义模式用 `custom_patch_styles`；为 `0` 时跳过 styles patch（页边距是否仍 `applyPageSetup`：一期 **仍按 EffectiveFormat.export.applyPageSetup**，默认 true，与 format 一致）
4. 其余写题/章/参考文献逻辑不变，继续读 EffectiveFormat

## 7. 前端（AiSchoolWeb）

入口：论文生成页「排版」抽屉 → `PaperFormatPanel.vue`。

### 7.1 UI

1. 模板区增加 **「上传本校模板」**
2. 流程：选 docx → 弹层（版式表单预填当前 effective + 勾选「强制用版式参数覆盖模板样式」，默认勾选）→ 提交 POST custom-docx
3. 自定义模式展示：文件名、`patchStyles` 状态；操作「更换文件」「清除自定义」
4. 学校模板下拉：custom 模式下选择学校项 → `ElMessageBox` 确认后再 PUT
5. 微调区、脏标记、导出前 `flushDirty` 行为保持不变

### 7.2 API 封装

在 `src/api/paper/index.ts` 增加 upload/delete custom-docx；扩展 `PaperSessionFormatVo` 类型字段。

## 8. 错误处理

| 场景 | 行为 |
|------|------|
| 未登录 / 非会话主人 | 401 / 403 |
| 非 docx / 超 10MB / 损坏 | 400，中文明确错误 |
| format 非法或超范围 | 400 |
| 自定义文件丢失 | 导出失败提示重新上传 |
| 删文件失败 | 仍清空 DB `custom_*`，记 warn 日志 |
| 租户 | 不新增租户字段；沿用 `paper_session` excludes |

## 9. 测试要点

1. 上传 docx + format + patch=true → GET `mode=custom`；导出字体随 format
2. patch=false → 导出不强制 patch styles
3. 微调保存 / reset → 只动 override；自定义 docx 仍在
4. 切学校模板确认后 → custom 清空，走学校模板
5. 清除自定义 → 无 custom；导出走默认模板逻辑
6. 越权上传他人 session → 拒绝
7. 超大文件 / 非 docx → 400

## 10. 实现顺序建议

1. SQL 迁移 + Entity / Session 领域字段
2. 上传/删除 API + 文件存储
3. GET/PUT format 与 WordExport 分支
4. `PaperFormatPanel` UI
5. 手工 E2E：上传 → 微调 → 导出 → 切回学校模板
