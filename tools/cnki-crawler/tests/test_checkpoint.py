from src.checkpoint import Checkpoint


def test_checkpoint_roundtrip(tmp_path):
    path = tmp_path / "cp.json"
    cp = Checkpoint(path)
    cp.mark_url_done("https://example.com/a")
    cp.set_keyword_page("软件工程", 3)
    cp.save()
    cp2 = Checkpoint(path)
    cp2.load()
    assert cp2.is_url_done("https://example.com/a")
    assert cp2.get_keyword_page("软件工程") == 3
