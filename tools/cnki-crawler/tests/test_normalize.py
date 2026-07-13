from src.normalize import title_hash


def test_title_hash_stable():
    a = title_hash("面向微服务的 软件架构研究！")
    b = title_hash("面向微服务的软件架构研究")
    assert a == b
