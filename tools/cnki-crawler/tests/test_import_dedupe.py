from src.import_dedupe import resolve_match


def test_dedupe_prefers_cnki_id():
    row = {"cnki_id": "A", "doi": "10.1", "title_hash": "h", "year": 2020}
    assert resolve_match({"A": 1}, {"10.1": 2}, {("h", 2020): 3}, row) == 1


def test_dedupe_falls_back_to_doi():
    row = {"cnki_id": None, "doi": "10.1", "title_hash": "h", "year": 2020}
    assert resolve_match({}, {"10.1": 2}, {("h", 2020): 3}, row) == 2


def test_dedupe_falls_back_to_hash_year():
    row = {"cnki_id": "", "doi": "", "title_hash": "h", "year": 2020}
    assert resolve_match({}, {}, {("h", 2020): 3}, row) == 3


def test_dedupe_insert_when_no_match():
    row = {"cnki_id": None, "doi": None, "title_hash": "x", "year": 2021}
    assert resolve_match({}, {}, {("h", 2020): 3}, row) is None
