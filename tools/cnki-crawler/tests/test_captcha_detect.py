from pathlib import Path

import pytest

from src.parse import CaptchaOrLoginError, detect_captcha_or_login


def test_list_html_with_security_verify_in_title_is_not_captcha():
    # 真实 brief/grid：论文题名含「安全验证」曾误杀整页
    html = """
    <div id="briefBox">
      <span class="pagerTitleCell">共找到 <em>424</em> 条结果</span>
      <table class="result-table-list">
        <tr>
          <td class="name">
            <a class="fz14" href="/kcms/detail">网站防护系统形式化模型构建与安全验证</a>
          </td>
        </tr>
      </table>
    </div>
    """
    detect_captcha_or_login(html)


def test_real_captcha_page_still_raises():
    html = "<html><body><h1>安全验证</h1><p>请拖动下方拼图完成验证</p></body></html>"
    with pytest.raises(CaptchaOrLoginError):
        detect_captcha_or_login(html)


def test_debug_brief_grid_fixture_if_present():
    path = Path("data/debug/brief_grid_1785143837.html")
    if not path.exists():
        pytest.skip("debug dump not present")
    detect_captcha_or_login(path.read_text(encoding="utf-8", errors="replace"))
