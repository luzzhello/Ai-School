from __future__ import annotations

import json
import logging
import time
from collections.abc import Iterator
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
DEFAULT_CROSS_IDS = "YSTT4HG0,LSTPFY1C,JUP3MUPD,MPMFIG1A,WQ0UVIAA,BLZOG7CK,PWFIRAGL,EMRPGLPA,NLBO1Z6R,NN3FJMUV"
DEFAULT_PRODUCT_STR = (
    "YSTT4HG0,LSTPFY1C,RMJLXHZ3,JQIRZIYA,JUP3MUPD,1UR4K4HZ,BPBAFJ5S,R79MZMCB,"
    "MPMFIG1A,WQ0UVIAA,NB3BWEHK,XVLO76FD,HR1YT1Z9,BLZOG7CK,PWFIRAGL,EMRPGLPA,"
    "J708GVCE,ML4DRIDX,NLBO1Z6R,NN3FJMUV,"
)
STARTER_RESOURCES = "CJFQ,CDMD,CIPD,CCND,CISD,SNAD,CCJD,BDZK,CCVD,CJFN"
PAGE_SIZE = 20


def search_index_url(keyword: str) -> str:
    qs = urlencode({"crossids": DEFAULT_CROSS_IDS, "korder": "SU", "kw": keyword})
    return f"{DEFAULT_RESULT_PATH}?{qs}"


def build_query_json(
    keyword: str,
    *,
    from_year: int | None,
    to_year: int | None,
    search_from: int,
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
    payload = {
        "Platform": "",
        "Resource": "CROSSDB",
        "Classid": DEFAULT_CLASS_ID,
        "Products": "",
        "QNode": {"QGroup": qgroups},
        "ExScope": 1,
        "SearchType": 2,
        "Rlang": "CHINESE",
        "KuaKuCode": DEFAULT_CROSS_IDS,
        "Expands": {},
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
) -> dict[str, str]:
    search_from = 1 if is_first else 4
    query_json = build_query_json(
        keyword, from_year=from_year, to_year=to_year, search_from=search_from
    )
    form: dict[str, str] = {
        "boolSearch": "true" if is_first else "false",
        "QueryJson": query_json,
        "queryJson": query_json,
        "pageNum": str(page),
        "pageSize": str(PAGE_SIZE),
        "CurPage": str(page),
        "RecordsCntPerPage": str(PAGE_SIZE),
        "sortField": "" if is_first else "PT",
        "sortType": "" if is_first else "desc",
        "dstyle": "listmode",
        "productStr": DEFAULT_PRODUCT_STR,
        "aside": f"主题：{keyword}" if is_first else "",
        "searchFrom": "资源范围：总库",
        "language": "",
        "uniplatform": "",
    }
    if turnpage:
        form["turnpage"] = turnpage
    return form


def assert_grid_response_ok(text: str) -> None:
    detect_captcha_or_login(text)
    if "非常抱歉，您访问的页面不存在" in text or ">401<" in text or "code\":401" in text:
        raise RateLimitError(
            "CNKI brief/grid 返回 401/无效页：请确认 Cookie 有效，并已完成暖场；"
            "该地址是 AJAX 接口，不能在浏览器直接打开"
        )
    if 'class="no-content"' in text or "暂无数据" in text:
        return


def warm_search_session(client: CnkiHttpClient, keyword: str) -> None:
    """模拟浏览器：首页 → ClientId → starter → 结果页，再请求 brief/grid。"""
    logger.info("warming CNKI search session for keyword=%s", keyword)
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
) -> Iterator[dict]:
    """Yield list-row dicts for a keyword, respecting checkpoint and max_per_keyword.

    翻页必须带上一页返回的 hidTurnPage；不能只靠 pageNum 跳到第 N 页。
    续跑策略：从第 1 页重新翻，用 cnki_id/url 跳过已抓，直到凑满本轮额度。
    """
    already = checkpoint.get_keyword_fetched(keyword)
    remain = max_per_keyword - already
    if remain <= 0:
        logger.info(
            "keyword=%s already fetched %s (>= max_per_keyword=%s), skip",
            keyword,
            already,
            max_per_keyword,
        )
        return

    warm_search_session(client, keyword)
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
        )
        html = client_post_search(client, payload, referer=search_index_url(keyword))
        assert_grid_response_ok(html)
        rows = parse_list_html(html)
        if not rows:
            empty_streak += 1
            logger.warning("keyword=%s page=%s empty list html_len=%s", keyword, page, len(html))
            if empty_streak >= 2 or page == 1:
                break
            # 尝试回到第一页重建 turnpage
            page = 1
            turnpage = ""
            continue
        empty_streak = 0

        new_on_page = 0
        for row in rows:
            url = row.get("detail_url") or ""
            cnki_id = row.get("cnki_id")
            if checkpoint.is_done(url=url or None, cnki_id=cnki_id):
                continue
            if from_year and row.get("year") and int(row["year"]) < from_year:
                continue
            if to_year and row.get("year") and int(row["year"]) > to_year:
                continue
            row["crawl_keyword"] = keyword
            yield row
            yielded += 1
            new_on_page += 1
            if yielded >= remain:
                break

        turnpage = _extract_turnpage(html)
        checkpoint.set_keyword_page(keyword, page + 1)
        checkpoint.set_keyword_turnpage(keyword, turnpage)
        checkpoint.save()
        logger.info(
            "keyword=%s page=%s rows=%s new=%s yielded=%s/%s turnpage=%s",
            keyword,
            page,
            len(rows),
            new_on_page,
            yielded,
            remain,
            "yes" if turnpage else "no",
        )
        if yielded >= remain:
            break
        if len(rows) < PAGE_SIZE:
            break
        if not turnpage:
            logger.warning("keyword=%s page=%s missing hidTurnPage, stop pagination", keyword, page)
            break
        page += 1


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
            detect_captcha_or_login(text)
            return text
        except CaptchaOrLoginError:
            raise
        except Exception as e:
            last_exc = e
    raise RateLimitError(str(last_exc) if last_exc else f"search failed: {SEARCH_URL}")
