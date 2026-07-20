from src.crawl_search import assert_grid_response_ok, _looks_like_cnki_401_page, _looks_like_grid_list
from src.http_client import RateLimitError
import pytest


def test_cite_count_401_is_not_error_page():
    html = """
    <table class="result-table-list">
      <tr><td class="quote"><span class="quoteNum">401</span></td></tr>
    </table>
    """
    assert _looks_like_grid_list(html)
    assert not _looks_like_cnki_401_page(html)
    assert_grid_response_ok(html)


def test_real_401_shell_detected():
    html = "<html><body><h1>401</h1><p>非常抱歉，您访问的页面不存在</p></body></html>"
    assert _looks_like_cnki_401_page(html)
    assert not _looks_like_grid_list(html)
    with pytest.raises(RateLimitError):
        assert_grid_response_ok(html)
