"""Unit tests for fractional index utilities."""

import pytest

from app.utils.fractional_index import after, before, between, first


def test_first_returns_nonempty_string():
    pos = first()
    assert isinstance(pos, str)
    assert len(pos) > 0


def test_after_sorts_greater_than_input():
    a = first()
    b = after(a)
    assert b > a


def test_before_sorts_less_than_input():
    a = first()
    b = before(a)
    assert b < a


def test_between_sorts_strictly_between():
    a = first()
    b = after(a)
    c = between(a, b)
    assert a < c < b


def test_between_requires_a_less_than_b():
    a = first()
    b = after(a)
    with pytest.raises(ValueError):
        between(b, a)


def test_between_raises_on_equal_inputs():
    a = first()
    with pytest.raises(ValueError):
        between(a, a)


def test_after_raises_on_empty():
    with pytest.raises(ValueError):
        after("")


def test_before_raises_on_empty():
    with pytest.raises(ValueError):
        before("")


def test_between_raises_on_empty_a():
    with pytest.raises(ValueError):
        between("", "z")


def test_between_raises_on_empty_b():
    with pytest.raises(ValueError):
        between("a", "")


def test_sequential_after_ordering():
    """100 sequential after() calls must remain in strict lexicographic order."""
    positions = [first()]
    for _ in range(99):
        positions.append(after(positions[-1]))
    for i in range(len(positions) - 1):
        assert positions[i] < positions[i + 1], (
            f"Order violated at index {i}: {positions[i]!r} >= {positions[i + 1]!r}"
        )


def test_sequential_before_ordering():
    """20 sequential before() calls must remain in strict lexicographic order (descending).

    Limited to 20 to stay within the single-character alphabet range of first() = 'n'.
    """
    positions = [first()]
    for _ in range(19):
        positions.append(before(positions[-1]))
    # Each new position is smaller, so the list is in descending order.
    for i in range(len(positions) - 1):
        assert positions[i] > positions[i + 1], (
            f"Order violated at index {i}: {positions[i]!r} <= {positions[i + 1]!r}"
        )


def test_between_ordering_100_inserts():
    """Insert 100 items between a fixed pair — all must maintain strict ordering."""
    lo = first()
    hi = after(after(lo))

    positions = [lo, hi]
    for _ in range(100):
        mid = between(positions[0], positions[1])
        positions.insert(1, mid)

    for i in range(len(positions) - 1):
        assert positions[i] < positions[i + 1], (
            f"Order violated at {i}: {positions[i]!r} >= {positions[i + 1]!r}"
        )


def test_1000_sequential_inserts_maintain_order():
    """1000 sequential after() inserts must be in strict ascending order."""
    positions = [first()]
    for _ in range(999):
        positions.append(after(positions[-1]))
    for i in range(len(positions) - 1):
        assert positions[i] < positions[i + 1], (
            f"Order violated at index {i}: {positions[i]!r} >= {positions[i + 1]!r}"
        )


def test_between_multiple_levels():
    """Nested between() calls must preserve order at each level."""
    a = first()
    b = after(a)
    c = between(a, b)
    d = between(a, c)
    e = between(c, b)

    assert a < d < c < e < b


def test_positions_are_strings():
    a = first()
    assert isinstance(after(a), str)
    assert isinstance(before(a), str)
    assert isinstance(between(a, after(a)), str)
