# 论文 Word 排版模板设计（学校模板 + 会话微调）

**日期**：2026-07-21  
**状态**：已定稿，待实现  
**范围**：多套「docx + 格式配置」学校模板；用户按会话选择并微调完整版式；导出按合并后的生效配置生成 Word

## 1. 背景与目标

### 现状

- 全站一份 `thesis-template.docx`（样式 ID 壳 + 可选大纲）
- 字体/字号/行距/页边距等几乎全部硬编码在 `WordExportService`（大连海洋版式）
- `patchTemplateStyles` 会覆盖模板原字体，用户换 docx 也改不了观感
- 用户端无模板选择、无排版微调；导出 API 无格式参数

### 目标

1. **管理端**维护多套学校模板：每套 = **docx 文件 + 完整版式配置**
2. **用户端**按会话选择模板，并可微调完整版式（字体、字号、行距、缩进、页边距、对齐等）
3. **导出**读取「默认 ← 模板 ← 会话覆盖」合并后的生效配置，替换硬编码常量
4. 未选模板的旧会话仍可导出（走内置默认 = 当前大连海洋规则）

### 非目标（一期不做）

- 封面可视化设计器、复杂页眉页脚动态域
- 模板变更后批量回写历史会话快照冻结
- 用户级「我的默认模板」独立偏好表（可用「设为会话默认」二期加）
- 改大纲「默认模板」语义（TOC 来源仍独立于排版模板）

## 2. 方案选型

采用 **方案 A：模板表 + 会话 JSON 覆盖**。

| 方案 | 结论 |
|------|------|
| A. `paper_format_template` + `session.format_override_json` | **采用**：扩展易、覆盖语义清晰 |
| B. 全部拆成表字段列 | 拒绝：字段多、改一次迁一次库 |
| C. 仅会话存完整快照、无模板表 | 拒绝：无法运营多校模板 |

模板载体：**docx + format_json 叠加**；冲突时以 format 为准（继续 patch styles）。

## 3. 配置模型 `PaperFormatConfig`

所有数值单位在注释中约定；JSON 用 camelCase，与前端一致。

### 3.1 页面

| 字段 | 类型 | 默认（大连海洋） | 说明 |
|------|------|------------------|------|
| `page.paper` | string | `A4` | 一期仅 A4 |
| `page.marginTopMm` | number | 30 | |
| `page.marginBottomMm` | number | 25 | |
| `page.marginLeftMm` | number | 30 | |
| `page.marginRightMm` | number | 25 | |

### 3.2 字体族

| 字段 | 默认 | 说明 |
|------|------|------|
| `font.bodyEastAsia` | 宋体 | 中文正文 |
| `font.bodyAscii` | Times New Roman | 西文正文 |
| `font.headingEastAsia` | 黑体 | 标题中文 |
| `font.headingAscii` | Times New Roman | 标题西文 |
| `font.tableEastAsia` | 宋体 | 表内中文 |
| `font.tableAscii` | Times New Roman | 表内西文 |
| `font.code` | Consolas | 代码块 |
| `font.footerEastAsia` | 宋体 | 页脚 |

### 3.3 字号（磅 pt）

| 字段 | 默认 | 对应 |
|------|------|------|
| `fontSize.title` | 18 | 论文题目（小二约 18） |
| `fontSize.heading1` | 16 | 三号 |
| `fontSize.heading2` | 12 | 小四 |
| `fontSize.heading3` | 10.5 | 五号 |
| `fontSize.body` | 10.5 | 五号 |
| `fontSize.abstractLabel` | 10.5 | 摘要/关键词标签 |
| `fontSize.caption` | 9 | 图题/表题（小五） |
| `fontSize.reference` | 10.5 | 参考文献 |
| `fontSize.footer` | 9 | 页脚页码 |
| `fontSize.toc` | 10.5 | 目录项 |

### 3.4 段落与标题

| 字段 | 默认 | 说明 |
|------|------|------|
| `paragraph.lineSpacingPt` | 18 | 正文固定行距（磅）；一期主路径用固定值 |
| `paragraph.lineSpacingRule` | `exact` | `exact` / `auto`（倍行距时用 `lineSpacingMultiple`） |
| `paragraph.lineSpacingMultiple` | 1.5 | 仅 `auto` 时生效 |
| `paragraph.firstLineIndentChars` | 2 | 首行缩进（字符） |
| `paragraph.bodyAlign` | `both` | both/left/center/right |
| `heading.h1Align` | `center` | |
| `heading.h2Align` | `left` | |
| `heading.h3Align` | `left` | |
| `heading.h1Bold` | false | 与现实现一致：黑体不加粗 |
| `heading.h2Bold` | false | |
| `heading.h3Bold` | false | |
| `heading.titleBold` | true | 论文题目 |
| `heading.h1SpacingBeforePt` 等（H1–H3 段前段后） | 与当前 `WordExportService` 实测值一致 | 写入 `PaperFormatDefaults`；实现计划阶段从代码抽出具体数字 |

### 3.5 导出行为开关

| 字段 | 默认 | 说明 |
|------|------|------|
| `export.patchTemplateStyles` | true | 用生效配置覆盖 styles.xml |
| `export.applyPageSetup` | true | 用配置写 sectPr 边距 |

### 3.6 校验

- 页边距：5–50 mm
- 字号：6–72 pt
- 行距固定值：10–40 pt；倍行距：1.0–3.0
- 字体名：非空；前端提供常用下拉，允许自定义输入
- 非法值拒绝保存；导出前再校验一次，失败返回明确错误

### 3.7 合并规则

```text
EffectiveFormat = deepMerge(DefaultFormat, template.format_json, session.format_override_json)
```

- `null` / 缺省字段不覆盖上层
- 会话「恢复模板默认」= 清空 `format_override_json`
- 切换模板：默认**清空** override（避免上一校边距残留）；UI 二次确认

## 4. 数据模型

### 4.1 表 `paper_format_template`

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | bigint PK | |
| `name` | varchar(100) | 展示名，如「大连海洋大学」 |
| `school_name` | varchar(100) | 可选校名备注 |
| `is_default` | tinyint | 全站默认（仅一条为 1） |
| `status` | char(1) | 0停用 1启用 |
| `docx_path` | varchar(500) | 相对 upload 的存储路径 |
| `docx_original_name` | varchar(255) | 上传原名 |
| `docx_size` | bigint | |
| `format_json` | mediumtext | `PaperFormatConfig` JSON |
| `style_mapping_json` | varchar(2000) | 可选缓存：normal/heading1… styleId |
| `remark` | varchar(500) | |
| `create_by` / `create_time` / `update_by` / `update_time` | 常规 | |

索引：`status`；唯一约束建议应用层保证 `is_default=1` 仅一条。

文件落盘建议：`{upload}/paper/format-templates/{id}/thesis-template.docx`（及可选 unpacked 缓存）。

### 4.2 会话表扩展 `paper_session`

| 列 | 类型 | 说明 |
|----|------|------|
| `format_template_id` | bigint NULL | 外键逻辑关联模板；NULL=内置默认 |
| `format_override_json` | mediumtext NULL | 仅差异字段 |

与现有「大纲默认模板」(`useDefaultTemplate` / TOC) **无关**，命名上区分「排版模板」。

### 4.3 内置默认

- 代码内 `PaperFormatDefaults.DALIAN_OCEAN`（与当前硬编码一致）
- 迁移脚本插入一条 `is_default=1` 的「大连海洋大学」模板：docx 从现有 `resources/paper/thesis-template.docx` 拷贝，`format_json` = 同一默认

### 4.4 与旧 `PaperTemplateService` 关系

一期演进：

1. 新服务 `PaperFormatTemplateService` 管多模板
2. 导出优先：`session.format_template_id` → 新表 docx；否则 fallback 旧全局目录 / classpath
3. 管理端旧「单文件上传页」改为「模板列表 + 编辑」；旧 API 可标记废弃或做适配转发到默认模板

## 5. API

### 5.1 管理端

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/paper/format-template/list` | 列表 |
| GET | `/api/paper/format-template/{id}` | 详情（含 format_json） |
| POST | `/api/paper/format-template` | 新建（可先无 docx，用默认壳） |
| PUT | `/api/paper/format-template/{id}` | 更新元信息 + format_json |
| POST | `/api/paper/format-template/{id}/docx` | 上传/替换 docx |
| POST | `/api/paper/format-template/{id}/set-default` | 设默认 |
| PUT | `/api/paper/format-template/{id}/status` | 启停 |
| GET | `/api/paper/format-template/{id}/download` | 下载 docx |
| DELETE | `/api/paper/format-template/{id}` | 软删或硬删（有会话引用则禁止或仅停用） |

权限：沿用/扩展 `system:paperTemplate:*` 或新 `system:paperFormatTemplate:*`。

### 5.2 用户端

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/paper/format-template/options` | 启用中的模板简表（id/name/isDefault） |
| GET | `/api/paper/session/{id}/format` | 返回 templateId、override、**effective** 合并结果、defaults |
| PUT | `/api/paper/session/{id}/format` | body: `{ templateId?, override? }`；切换模板可带 `clearOverride: true` |
| POST | `/api/paper/session/{id}/format/reset` | 清空 override |
| GET | `/api/paper/export/{sessionId}` | **不变**；服务端读会话配置导出 |

## 6. 导出改造（`WordExportService`）

1. 加载会话 → 解析 `EffectiveFormat`
2. `openCleanTemplateDocument`：打开对应模板 docx（清正文逻辑保留）
3. `applyPageSetup(effective)` 替代 `applyDalianOceanPageSetup` 常量
4. `patchTemplateStyles(effective)` 按配置写字体字号
5. 写题目/摘要/目录/章节/参考文献/表/图/页脚：全部从 `effective` 取字体字号行距对齐
6. 删除或降级为「仅 Default 来源」的 `private static final` 字体常量

样式 ID 仍从该 docx 的 `styles.xml` 解析（`PaperTemplateStyleMapping`），与 format 正交。

## 7. 前端

### 7.1 管理端（AiSchoolAdminWeb）

- 页面：模板列表 → 编辑页
- 编辑：基础信息 + docx 上传 + 分组表单（页面/字体/字号/段落/标题）
- 可选：JSON 高级编辑（校验后保存）
- 「从默认填充」按钮

### 7.2 用户端（AiSchoolWeb）

- 论文生成流程中增加「排版」步骤或导出前抽屉：
  - 下拉选择学校模板
  - 展开「微调」：完整版式表单（绑定 override，展示 effective 预览文案）
  - 「恢复模板默认」
- 「导出 Word」不传格式体，只依赖已保存的会话配置
- 字体提示：常见中文字体需本机安装

## 8. 一期交付边界

**做**

- 表结构 + 默认数据迁移
- 管理端多模板 CRUD + docx
- 用户选模板 + 完整版式 override
- WordExport 读配置
- 旧会话 NULL template → 内置默认，行为与今一致

**不做**

- 封面设计器、页眉校名变量
- 历史会话 format 快照冻结
- 用户全局默认模板表
- 自动从上传 docx「识别」字体填入 format（可二期）

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 缺字体导致 Word 显示异常 | UI 常用字体列表 + 说明；导出不拦截 |
| 模板 docx 与 format 不一致 | 明确 format 优先；patch 开启时覆盖 |
| 极端字号/边距导致布局崩 | 服务端范围校验 |
| 切换模板残留 override | 默认清空 + 确认框 |
| 旧单模板管理页并存 | 迁移说明；默认模板承接旧文件 |

## 10. 验收标准

1. 管理端可创建至少 2 套模板，上传不同 docx，配置不同正文字体/字号/边距
2. 用户会话 A 选模板 1、会话 B 选模板 2，导出 Word 字体与边距分别符合各自 effective 配置
3. 会话微调只改正文字号后，导出仅该字段变化，其余仍跟模板
4. 「恢复模板默认」后导出与纯模板一致
5. 未设置 `format_template_id` 的旧会话导出结果与改造前大连海洋版式一致（抽样对比）
6. 停用模板后 `options` 不可选；**已绑定该模板的会话仍按原 template_id 导出**（不停用已产生文档的版式）；删除模板若仍有会话引用则禁止，仅允许停用

## 11. 已确认决策

- 粒度：学校模板 + 会话微调
- 可配项：完整版式（字体/字号/行距/缩进/边距/对齐）
- 载体：docx + format_json
- 存储：模板表 + 会话 override JSON
- 合并：Default ← Template ← Session override
- 一期不做用户级偏好表、不做历史冻结快照
