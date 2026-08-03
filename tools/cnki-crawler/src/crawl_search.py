from __future__ import annotations

import json
import logging
import time
from collections.abc import Iterator
from pathlib import Path
from urllib.parse import urlencode

from src.checkpoint import Checkpoint
from src.http_client import CnkiHttpClient, RateLimitError
from src.parse import CaptchaOrLoginError, detect_captcha_or_login, parse_list_html

logger = logging.getLogger(__name__)

# AJAX 列表接口：不可在浏览器地址栏直接打开（会显示 401 假页面）
SEARCH_URL = "https://kns.cnki.net/kns8s/brief/grid"
HOME_URL = "https://www.cnki.net"
CLIENT_ID_URL = "https://recsys.cnki.net/RCDService/api/UtilityOpenApi/GenerateClientID"
STARTER_URL = "https://kns.cnki.net/starter"
DEFAULT_RESULT_PATH = "https://kns.cnki.net/kns8s/defaultresult/index"

DEFAULT_CLASS_ID = "WD0FTY92"
# 与浏览器总库检索抓包一致（含 View=changeDBCh 时的 crossids 顺序）
DEFAULT_CROSS_IDS = (
    "YSTT4HG0,LSTPFY1C,EMRPGLPA,JUP3MUPD,MPMFIG1A,WQ0UVIAA,BLZOG7CK,PWFIRAGL,NLBO1Z6R,NN3FJMUV"
)
# 外文总库（浏览器点「外文」抓包）
FOREIGN_CROSS_IDS = (
    "YSTT4HG0,LSTPFY1C,EMRPGLPA,JUP3MUPD,MPMFIG1A,WQ0UVIAA,BLZOG7CK,PWFIRAGL,NN3FJMUV,NLBO1Z6R"
)
# 浏览器首页检索 productStr 为空；长串易触发风控
DEFAULT_PRODUCT_STR = ""
STARTER_RESOURCES = "CJFQ,CDMD,CIPD,CCND,CISD,SNAD,CCJD,BDZK,CCVD,CJFN"
PAGE_SIZE = 20

# chinese = 总库中文；foreign = 总库外文
RLANG_CHINESE = "CHINESE"
RLANG_FOREIGN = "FOREIGN"


def normalize_rlang(value: str | None) -> str:
    raw = (value or "chinese").strip().lower()
    if raw in {"foreign", "en", "english", "外文", "fw"}:
        return RLANG_FOREIGN
    return RLANG_CHINESE


def checkpoint_keyword_key(keyword: str, rlang: str) -> str:
    """关键词进度键（中/外文已分文件，键即为词本身；rlang 保留以兼容调用方）。"""
    _ = rlang
    return keyword


def search_index_url(keyword: str) -> str:
    qs = urlencode({"crossids": DEFAULT_CROSS_IDS, "korder": "SU", "kw": keyword})
    return f"{DEFAULT_RESULT_PATH}?{qs}"


def build_query_json(
    keyword: str,
    *,
    from_year: int | None,
    to_year: int | None,
    search_from: int,
    rlang: str = RLANG_CHINESE,
) -> str:
    qgroups: list[dict] = [
        {
            "Key": "Subject",
            "Title": "",
            "Logic": 0,
            "Items": [
                {
                    "Field": "SU",
                    "Value": keyword,
                    "Operator": "TOPRANK",
                    "Logic": 0,
                    "Title": "主题",
                }
            ],
            "ChildItems": [],
        }
    ]
    if from_year or to_year:
        y0 = from_year or 1900
        y1 = to_year or 2100
        qgroups.append(
            {
                "Key": "ControlGroup",
                "Title": "",
                "Logic": 0,
                "Items": [
                    {
                        "Key": "span[value=PT]",
                        "Title": "发表时间",
                        "Logic": 0,
                        "Field": "PT",
                        "Operator": 7,
                        "Value": f"{y0:04d}-01-01",
                        "Value2": f"{y1:04d}-12-31",
                    }
                ],
                "ChildItems": [],
            }
        )
    rlang_n = normalize_rlang(rlang)
    kuaku = FOREIGN_CROSS_IDS if rlang_n == RLANG_FOREIGN else DEFAULT_CROSS_IDS
    payload: dict = {
        "Platform": "",
        "Resource": "CROSSDB",
        "Classid": DEFAULT_CLASS_ID,
        "Products": "",
        "QNode": {"QGroup": qgroups},
        "ExScope": 1,
        "SearchType": 2,
        "Rlang": rlang_n,
        "KuaKuCode": kuaku,
        "Expands": {},
        # 浏览器中文/外文总库检索均带 View=changeDBCh
        "View": "changeDBCh",
        "SearchFrom": search_from,
    }
    return json.dumps(payload, ensure_ascii=False)


def build_search_payload(
    keyword: str,
    page: int,
    from_year: int | None,
    to_year: int | None,
    *,
    is_first: bool,
    turnpage: str = "",
    rlang: str = RLANG_CHINESE,
) -> dict[str, str]:
    search_from = 1 if is_first else 4
    rlang_n = normalize_rlang(rlang)
    query_json = build_query_json(
        keyword,
        from_year=from_year,
        to_year=to_year,
        search_from=search_from,
        rlang=rlang_n,
    )
    # 字段集对齐浏览器 DevTools「Copy as fetch」；productStr 为空串
    form: dict[str, str] = {
        "boolSearch": "true" if is_first else "false",
        "QueryJson": query_json,
        "pageNum": str(page),
        "pageSize": str(PAGE_SIZE),
        "sortField": "" if is_first else "PT",
        "sortType": "" if is_first else "desc",
        "dstyle": "listmode",
        "productStr": "",
        "aside": f"(主题：{keyword})" if is_first else "",
        "searchFrom": "资源范围：总库",
        "subject": "",
        "language": "",
        "uniplatform": "",
        "CurPage": str(page),
    }
    if turnpage:
        form["turnpage"] = turnpage
    return form


def _looks_like_grid_list(text: str) -> bool:
    """Valid kns8s AJAX list HTML has these markers (not the browser 401 shell page)."""
    return (
        "result-table-list" in text
        or "pagerTitleCell" in text
        or 'id="hidTurnPage"' in text
        or "name=\"hidTurnPage\"" in text
        or 'class="no-content"' in text
        or "暂无数据" in text
        or "icon-collect" in text
    )


def _looks_like_cnki_401_page(text: str) -> bool:
    """Real CNKI 401 /「页面不存在」壳页。勿用 lone `>401<`——会被引次数误伤。"""
    if "非常抱歉，您访问的页面不存在" in text:
        return True
    if "页面不存在" in text and ("401" in text or "HTTP Status" in text):
        return True
    # JSON error body from some gateways
    if '"code":401' in text.replace(" ", "") or '"status":401' in text.replace(" ", ""):
        return True
    return False


def dump_grid_debug(text: str, *, reason: str) -> Path | None:
    try:
        debug_dir = Path("data") / "debug"
        debug_dir.mkdir(parents=True, exist_ok=True)
        path = debug_dir / f"brief_grid_{int(time.time())}.html"
        path.write_text(text or "", encoding="utf-8", errors="replace")
        logger.error("saved brief/grid response (%s) to %s (len=%s)", reason, path, len(text or ""))
        return path
    except Exception as e:
        logger.warning("dump brief/grid debug failed: %s", e)
        return None


def assert_grid_response_ok(text: str) -> None:
    try:
        detect_captcha_or_login(text)
    except CaptchaOrLoginError:
        dump_grid_debug(text, reason="captcha-or-login")
        raise
    if _looks_like_grid_list(text):
        return
    if _looks_like_cnki_401_page(text):
        dump_grid_debug(text, reason="401-page")
        raise RateLimitError(
            "CNKI brief/grid 返回 401/无效页：请确认 Cookie 有效，并已完成暖场；"
            "该地址是 AJAX 接口，不能在浏览器直接打开"
        )
    # 既不像列表也不像明确 401：可能是风控/改版 HTML，落盘后空列表流程可继续或停
    if text and len(text) > 80:
        dump_grid_debug(text, reason="unknown-body")
        raise RateLimitError(
            "CNKI brief/grid 返回非列表 HTML（可能 Cookie 失效或页面改版）；"
            f"已写入 data/debug/，html_len={len(text)}"
        )


def warm_search_session(
    client: CnkiHttpClient,
    keyword: str,
    *,
    rlang: str = RLANG_CHINESE,
) -> None:
    """模拟浏览器：首页 → ClientId → starter → 结果页，再请求 brief/grid。"""
    rlang_n = normalize_rlang(rlang)
    logger.info("warming CNKI search session for keyword=%s rlang=%s", keyword, rlang_n)
    client.get(HOME_URL, is_detail=False)
    try:
        # ClientID 接口返回 JSON，不走 HTML captcha 检测
        client._sleep(client.list_delay_sec)  # noqa: SLF001
        resp = client._client.get(CLIENT_ID_URL, headers={"Origin": HOME_URL, "Referer": HOME_URL + "/"})  # noqa: SLF001
        if resp.status_code == 200:
            try:
                data = resp.json()
                cid = (data or {}).get("Data") or ""
                if cid:
                    client._client.cookies.set("Ecp_ClientId", cid, domain="cnki.net")  # noqa: SLF001
            except Exception:
                logger.warning("parse ClientID response failed, continue with Cookie")
    except Exception as e:
        logger.warning("ClientID warm-up skipped: %s", e)

    starter_qs = urlencode(
        {"rc": STARTER_RESOURCES, "kw": keyword, "rt": "crossdb", "fd": "SU"}
    )
    client.get(f"{STARTER_URL}?{starter_qs}", is_detail=False)
    client.get(search_index_url(keyword), is_detail=False)


def iter_search_rows(
    client: CnkiHttpClient,
    checkpoint: Checkpoint,
    keyword: str,
    *,
    max_per_keyword: int,
    from_year: int | None,
    to_year: int | None,
    rlang: str = RLANG_CHINESE,
) -> Iterator[dict]:
    """Yield list-row dicts for a keyword, respecting checkpoint and max_per_keyword.

    翻页必须带上一页返回的 hidTurnPage；不能只靠 pageNum 跳到第 N 页。
    续跑策略：从第 1 页重新翻，用 cnki_id/url 跳过已抓，直到凑满本轮额度。
    """
    rlang_n = normalize_rlang(rlang)
    cp_key = checkpoint_keyword_key(keyword, rlang_n)
    already = checkpoint.get_keyword_fetched(cp_key)
    remain = max_per_keyword - already
    if remain <= 0:
        logger.info(
            "keyword=%s rlang=%s already fetched %s (>= max_per_keyword=%s), skip",
            keyword,
            rlang_n,
            already,
            max_per_keyword,
        )
        return

    warm_search_session(client, keyword, rlang=rlang_n)
    page = 1
    yielded = 0
    turnpage = ""
    empty_streak = 0
    while yielded < remain:
        is_first = page == 1
        payload = build_search_payload(
            keyword,
            page,
            from_year,
            to_year,
            is_first=is_first,
            turnpage="" if is_first else turnpage,
            rlang=rlang_n,
        )
        html = client_post_search(client, payload, referer=search_index_url(keyword))
        assert_grid_response_ok(html)
        rows = parse_list_html(html)
        if not rows:
            empty_streak += 1
            logger.warning(
                "keyword=%s rlang=%s page=%s empty list html_len=%s",
                keyword,
                rlang_n,
                page,
                len(html),
            )
            if empty_streak >= 2 or page == 1:
                if page == 1 and empty_streak >= 1:
                    logger.warning(
                        "crawl empty first page keyword=%s rlang=%s html_len=%s "
                        "(no results or parse failure)",
                        keyword,
                        rlang_n,
                        len(html),
                    )
                break
            # 尝试回到第一页重建 turnpage
            page = 1
            turnpage = ""
            continue
        empty_streak = 0

        new_on_page = 0
        skipped_done = 0
        skipped_year = 0
        for row in rows:
            url = row.get("detail_url") or ""
            cnki_id = row.get("cnki_id")
            if checkpoint.is_done(url=url or None, cnki_id=cnki_id):
                skipped_done += 1
                continue
            if from_year and row.get("year") and int(row["year"]) < from_year:
                skipped_year += 1
                continue
            if to_year and row.get("year") and int(row["year"]) > to_year:
                skipped_year += 1
                continue
            row["crawl_keyword"] = keyword
            row["checkpoint_key"] = cp_key
            row["search_rlang"] = rlang_n
            yield row
            yielded += 1
            new_on_page += 1
            if yielded >= remain:
                break

        turnpage = _extract_turnpage(html)
        checkpoint.set_keyword_page(cp_key, page + 1)
        checkpoint.set_keyword_turnpage(cp_key, turnpage)
        checkpoint.save()
        logger.info(
            "keyword=%s rlang=%s page=%s rows=%s new=%s skip_done=%s skip_year=%s yielded=%s/%s turnpage=%s",
            keyword,
            rlang_n,
            page,
            len(rows),
            new_on_page,
            skipped_done,
            skipped_year,
            yielded,
            remain,
            "yes" if turnpage else "no",
        )
        if yielded >= remain:
            break
        if len(rows) < PAGE_SIZE:
            break
        if not turnpage:
            logger.warning(
                "keyword=%s rlang=%s page=%s missing hidTurnPage, stop pagination",
                keyword,
                rlang_n,
                page,
            )
            break
        page += 1

    if yielded == 0:
        logger.warning(
            "keyword=%s rlang=%s yielded=0 after search "
            "(empty list / all checkpoint-done / year filtered); already=%s remain=%s",
            keyword,
            rlang_n,
            already,
            remain,
        )
    else:
        logger.info(
            "keyword=%s rlang=%s search done yielded=%s remain_was=%s",
            keyword,
            rlang_n,
            yielded,
            remain,
        )


def _extract_turnpage(html: str) -> str:
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(html, "lxml")
    for sel in ("#hidTurnPage", "input[name=hidTurnPage]", "input#turnpage", "input[name=turnpage]"):
        el = soup.select_one(sel)
        if el and el.get("value"):
            return str(el.get("value")).strip()
    return ""


def client_post_search(client: CnkiHttpClient, payload: dict, *, referer: str) -> str:
    """POST search form with AJAX headers (brief/grid is not a normal page)."""
    client._sleep(client.list_delay_sec)  # noqa: SLF001
    delays = [5.0, 15.0, 45.0]
    last_exc: Exception | None = None
    headers = {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Origin": "https://kns.cnki.net",
        "Referer": referer,
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "*/*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "sec-fetch-dest": "empty",
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-origin",
    }
    for backoff in [0.0, *delays]:
        if backoff:
            time.sleep(backoff)
        try:
            resp = client._client.post(SEARCH_URL, data=payload, headers=headers)  # noqa: SLF001
            if resp.status_code == 429:
                last_exc = RateLimitError(f"HTTP 429 for {SEARCH_URL}")
                continue
            if resp.status_code == 401:
                last_exc = RateLimitError(
                    "HTTP 401 for brief/grid — Cookie 无效或未暖场；请重新登录知网复制 Cookie"
                )
                continue
            resp.raise_for_status()
            text = resp.text
            try:
                detect_captcha_or_login(text)
            except CaptchaOrLoginError:
                dump_grid_debug(text, reason="captcha-or-login")
                raise
            return text
        except CaptchaOrLoginError:
            raise
        except Exception as e:
            last_exc = e
    raise RateLimitError(str(last_exc) if last_exc else f"search failed: {SEARCH_URL}")
