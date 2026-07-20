# CNKI 软件工程文献元数据爬虫



独立工具：按关键词从中国知网采集论文**元数据**（不含 PDF/CAJ），写出 JSONL，再导入 MySQL。



- 中文 → `lit_paper` / `data/papers.jsonl` / `keywords/se.txt`

- 外文 → `lit_paper_en` / `data/papers_en.jsonl` / `keywords/se-en.txt`



论文生成检索时：**中文查中文表，英文查英文表**（不再支持「全部」混合查）。



## 合规与风险



- 仅采集元数据与参考文献条目，**不下载全文**。

- 使用个人知网账号时务必限速；建议详情间隔 ≥6s+抖动，单日详情别贪多。

- 遵守知网服务条款与账号规范；详情遇验证码会先存列表字段、暂停后继续，连续多次再停并保留 checkpoint。

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

| `search_lang` | `chinese` / `foreign`（总库中文或外文） |

| `output_jsonl` / `output_jsonl_en` | 中文/外文 JSONL |

| `checkpoint_path` / `checkpoint_path_en` | 中文/外文断点（分文件） |



## 常用命令



### 爬取中文（默认总库「中文」+ `keywords/se.txt`）



```bash

# 用词表批量爬

python -m src --config config.yaml crawl



# 单关键词

python -m src --config config.yaml crawl --keyword 软件工程 --max 50



# 只抓列表、不进详情（少触发验证码）

python -m src --config config.yaml crawl --list-only --keyword springboot

```



### 爬取外文（总库「外文」`Rlang=FOREIGN` + `keywords/se-en.txt`）



```bash

# 用英文词表批量爬 → data/papers_en.jsonl

python -m src --config config.yaml crawl --search-lang foreign



# 单关键词

python -m src --config config.yaml crawl --search-lang foreign --keyword "software architecture" --max 50



# 指定英文词表

python -m src --config config.yaml crawl --search-lang foreign --keywords-file keywords/se-en.txt

```



也可在 `config.yaml` 写死：



```yaml

search_lang: foreign

```



### 补 DOI / 期号 / 页码（不删 checkpoint）



详情页用 `.rowtit` 解析；期号常来自 `bar.cnki.net` 结算页 `.article-source`。



```bash

# 中文 JSONL 回填（默认走 dazhong/fee）

python scripts/backfill_doi.py --config config.yaml --jsonl data/papers.jsonl --limit 50



# 外文

python scripts/backfill_doi.py --config config.yaml --jsonl data/papers_en.jsonl --limit 50

```



### 导入 MySQL



建表（库上执行一次）：



- 中文：`docs/script/sql/update/updat-lit-paper.sql`

- 著录字段补丁：`updat-lit-paper-biblio-fields.sql`、`updat-lit-paper-widen-source.sql`

- 外文：`docs/script/sql/update/updat-lit-paper-en.sql`



```bash

# 中文 → lit_paper

python scripts/import_to_mysql.py --host 159.75.166.190 --port 3307 --user root --password 123QWER. --database ai_sc --jsonl data/papers.jsonl



# 外文 → lit_paper_en

python scripts/import_to_mysql.py --host 159.75.166.190 --port 3307 --user root --password 123QWER. --database ai_sc --jsonl data/papers_en.jsonl --lang en
```

去重顺序（各表独立）：`cnki_id` → `doi` → (`title_hash` + `year`)。



## 验证码频繁？



常见原因是间隔太短。个人账号建议：



```yaml

list_delay_sec: 3.0

detail_delay_sec: 6.0

delay_jitter_sec: 2.0

daily_detail_limit: 200

```



触发验证后：浏览器打开知网完成验证 → 更新 Cookie → 再跑（断点会续）。



断点分文件：中文 `data/checkpoint.json`，外文 `data/checkpoint_en.json`（可删其一独立重爬）。首次跑外文时若旧单文件含 `词::foreign`，会自动拆到外文断点。



## 知网改版



列表/详情解析集中在 `src/parse.py`。DOM 变化时：



1. 保存真实 HTML 到 `tests/fixtures/`

2. 更新选择器

3. 跑 `pytest`



检索 POST：`https://kns.cnki.net/kns8s/brief/grid`（AJAX）。外文查询关键为 `QueryJson.Rlang=FOREIGN`、`View=changeDBCh`。



**不要在浏览器地址栏直接打开该 URL**，会显示 401「页面不存在」。爬虫会暖场后再 POST。



## 测试



```bash

python -m pytest -q

```

# 中文（默认 se.txt → papers.jsonl）
python -m src --config config.yaml crawl
# 外文（默认 se-en.txt → papers_en.jsonl）
python -m src --config config.yaml crawl --search-lang foreign

