# CNKI 软件工程文献元数据爬虫

独立工具：按关键词从中国知网采集论文**元数据**（不含 PDF/CAJ），写出 JSONL，再导入 MySQL `lit_paper`，供论文生成「文献库检索」使用。

## 合规与风险

- 仅采集元数据与参考文献条目，**不下载全文**。
- 使用个人知网账号时务必限速；默认详情间隔约 4s+抖动，单日详情上限可配。
- 遵守知网服务条款与账号规范；遇验证码/登录页会停爬并保留 checkpoint。
- Cookie、`config.yaml`、JSONL 数据不要提交到 Git。

## 环境

```bash
cd tools/cnki-crawler
python -m venv .venv
# Windows:
.\.venv\Scripts\activate
pip install -r requirements.txt
```

## 配置 Cookie

1. 浏览器登录知网，打开 DevTools → Network，复制请求头里的 `Cookie`。
2. 复制配置：

```bash
copy config.example.yaml config.yaml
```

3. 将 `cookie` 改为真实 Cookie 字符串。

主要参数：

| 项 | 含义 |
|----|------|
| `list_delay_sec` / `detail_delay_sec` | 列表/详情基础间隔 |
| `delay_jitter_sec` | 随机抖动 |
| `daily_detail_limit` | 单日详情上限 |
| `max_per_keyword` | 每个关键词最多抓取条数 |
| `from_year` / `to_year` | 年份过滤 |
| `output_jsonl` / `checkpoint_path` | 输出与断点文件 |

## 运行

```bash
# 全量按 keywords/se.txt
python -m src --config config.yaml crawl

# 单关键词试跑 5 条
python -m src --config config.yaml crawl --keyword 软件测试 --max 5
```

断点：`data/checkpoint.json` 记录已抓 URL、关键词页码、当日详情计数。中断后重新运行会跳过已完成 URL。

## 导入 MySQL

先执行 `docs/script/sql/update/updat-lit-paper.sql`，再：

```bash
python scripts/import_to_mysql.py --host 127.0.0.1 --port 3306 --user root --password xxx --database ry-vue --jsonl data/papers.jsonl
```

去重顺序：`cnki_id` → `doi` → (`title_hash` + `year`)。

## 知网改版

列表/详情解析集中在 `src/parse.py`。DOM 变化时：

1. 保存真实 HTML 到 `tests/fixtures/`
2. 更新选择器
3. 跑 `pytest`

检索 POST 地址当前为 `https://kns.cnki.net/kns8s/brief/grid`（AJAX 接口）。

**注意：不要在浏览器地址栏直接打开该 URL**，会显示 401「页面不存在」。
爬虫会先访问首页 / ClientId / 结果页暖场，再带 Cookie + `X-Requested-With` 去 POST。
若仍 401：重新登录知网，更新 `config.yaml` 里的 Cookie 后再试。

若线上接口再变更，同步改 `src/crawl_search.py` 并更新本文。

## 测试

```bash
python -m pytest -q
```
