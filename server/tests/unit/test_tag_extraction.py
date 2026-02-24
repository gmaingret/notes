"""Unit tests for tag extraction from bullet content."""

from app.services.tag_service import extract_tags


def test_single_tag():
    assert extract_tags("Hello #world") == {"world"}


def test_multiple_tags():
    assert extract_tags("#foo and #bar and #baz") == {"foo", "bar", "baz"}


def test_tags_with_hyphens():
    assert extract_tags("item #to-do") == {"to-do"}


def test_tags_with_numbers():
    assert extract_tags("see #item1 and #chapter2") == {"item1", "chapter2"}


def test_tags_normalised_to_lowercase():
    assert extract_tags("#Hello #WORLD #MixedCase") == {"hello", "world", "mixedcase"}


def test_no_tags_in_plain_text():
    assert extract_tags("just plain text here") == set()


def test_no_false_positive_on_http_url():
    """A URL like http://example.com should not produce a tag."""
    assert extract_tags("see http://example.com for details") == set()


def test_no_false_positive_on_https_url():
    assert extract_tags("https://example.com/path?q=1") == set()


def test_no_false_positive_on_url_with_hash_fragment():
    """URL fragment (e.g. #anchor) should not be extracted as a tag."""
    assert extract_tags("visit https://docs.example.com/page#section") == set()


def test_tag_at_start_of_string():
    assert extract_tags("#startup idea") == {"startup"}


def test_tag_at_end_of_string():
    assert extract_tags("some idea #important") == {"important"}


def test_duplicate_tags_deduplicated():
    assert extract_tags("#todo and #todo again") == {"todo"}


def test_tag_after_punctuation():
    """Tags following punctuation (comma, period) should be extracted."""
    assert extract_tags("done, #done. #next!") == {"done", "next"}


def test_empty_string():
    assert extract_tags("") == set()


def test_hash_only_not_a_tag():
    """A lone '#' with no word character following is not a tag."""
    assert extract_tags("price is 100# off") == set()


def test_underscore_in_tag():
    assert extract_tags("#my_tag") == {"my_tag"}
