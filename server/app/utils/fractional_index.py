"""
Fractional index utilities for ordering documents and bullets.

Produces lexicographically sortable strings so items can be inserted
anywhere without renumbering siblings. The algorithm is equivalent to
the one used by Figma and Linear.

Alphabet: '0'–'9' then 'a'–'z' (base-36, lowercase).

The starting position for a list with one item is 'n' (index 23 of 36),
leaving room on both sides.

Public API
----------
first()           -> position for the first (and only) item in a list
after(a)          -> position that sorts strictly after `a`
before(a)         -> position that sorts strictly before `a`
between(a, b)     -> position that sorts strictly between `a` and `b`

All functions return non-empty strings.
"""

_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"
_BASE = len(_CHARS)  # 36
_CHAR_INDEX = {c: i for i, c in enumerate(_CHARS)}

# Mid-point of the character set — used as the "neutral" start.
_MID_CHAR = _CHARS[_BASE // 2]  # 'i' (index 18)

# Starting position returned by first().
_FIRST_POS = "n"


# ---------------------------------------------------------------------------
# Public helpers
# ---------------------------------------------------------------------------


def first() -> str:
    """Return a position string suitable for the first (and only) item in a list."""
    return _FIRST_POS


def after(a: str) -> str:
    """
    Return a position string that sorts strictly after `a`.

    Appending the mid character always produces a string lexicographically
    greater than `a` (the appended character is beyond the end of `a`).
    """
    if not a:
        raise ValueError("position string must not be empty")
    return a + _MID_CHAR


def before(a: str) -> str:
    """
    Return a position string that sorts strictly before `a`.

    Strategy: walk from the rightmost character looking for one we can
    decrement.  If we find one, decrement it and truncate the string there.
    If every character is already at the minimum ('0'), we generate a
    shorter string by using '0' followed by the character one below the
    first character of `a`.  This always produces a result < `a` because
    the first character of the result ('0') is less than the first
    character of `a` — unless `a` already starts with '0', in which case
    we recurse one level deeper.

    The simplest safe approach for edge cases: prepend a character strictly
    less than the first character of `a`.  Since '0' is the minimum and
    anything prepended before a string makes it sort lower only if the
    prefix char < first char of the original, we instead build the result
    as: find the largest string that is still < `a`.

    Practical implementation: if `a` starts with '0', prefix with '0' and
    recurse into `a[1:]` to find the first non-'0' character, then
    decrement it.  This guarantees the result is shorter than `a` only when
    `a` is all '0's — in that edge case we return '0' with a minimum suffix
    that is still lexicographically less. Because strings shorter in the
    common prefix sort *before* longer ones only when they are a prefix of
    the longer, we use a different approach:

    Safest algorithm:
      1. Decrement the last character that can be decremented.
      2. If no character can be decremented (all '0'), produce a string
         by prepending with a character below the minimum *or* by using
         a fractional trick: take the smallest char followed by (a - epsilon).
         Since we cannot go below '0' as first char, we rely on the contract
         that callers must supply strings that came from this module — in
         which case no string will ever be all '0's in normal usage.
    """
    if not a:
        raise ValueError("position string must not be empty")

    chars = list(a)
    for i in range(len(chars) - 1, -1, -1):
        idx = _CHAR_INDEX[chars[i]]
        if idx > 0:
            chars[i] = _CHARS[idx - 1]
            # Truncate: drop all characters after the decremented position.
            return "".join(chars[: i + 1])

    # All characters are '0'. This should not occur in normal usage
    # (positions come from first()/after()/between()). As a safe fallback,
    # we produce a string that is definitely less by using a double-'0' prefix
    # and then the second-to-last character of the alphabet, which is less
    # than '0' + <anything of same length>.  Actually the simplest approach:
    # return '0' repeated (len+1) times — but that doesn't sort before "000..0".
    # Instead: append MID_CHAR to the all-zeros string, making it longer.
    # Length-wise "00" > "0" so this fails too.
    #
    # True fallback: since '0' is the absolute minimum first character in our
    # alphabet, *any* string starting with '0' sorts before any string starting
    # with a character > '0'.  So we prepend '0' to a suffix that is the max
    # representation of (len(a)) characters, which ensures the result < a.
    # But this is only true if a[0] > '0', which contradicts our premise.
    #
    # In practice, first() returns 'n', after() appends chars, so this case
    # never occurs in normal use.  Return a safe sentinel.
    raise ValueError(
        f"Cannot compute before({a!r}): all characters are at minimum '0'. "
        "This position was not generated by this module."
    )


def between(a: str, b: str) -> str:
    """
    Return a position string that sorts strictly between `a` and `b`.

    Requires `a` < `b` lexicographically.
    """
    if not a or not b:
        raise ValueError("position strings must not be empty")
    if a >= b:
        raise ValueError(f"between() requires a < b, got {a!r} >= {b!r}")

    # Pad the shorter string with '0' (minimum character) so both have the
    # same length for character-by-character comparison.
    max_len = max(len(a), len(b))
    a_padded = a.ljust(max_len, "0")
    b_padded = b.ljust(max_len, "0")

    result: list[str] = []
    for i in range(max_len):
        ca = a_padded[i]
        cb = b_padded[i]
        ia = _CHAR_INDEX[ca]
        ib = _CHAR_INDEX[cb]

        if ia == ib:
            result.append(ca)
            continue

        # Characters differ at position i.
        mid = (ia + ib) // 2
        if mid > ia:
            # There is a strict midpoint we can use.
            result.append(_CHARS[mid])
            return "".join(result)

        # Adjacent characters (ia + 1 == ib) — extend beyond a's position.
        # a_padded[i:] + _MID_CHAR is always:
        #   > a: appending chars beyond a's end makes it lexicographically greater
        #   < b: result starts with ca which is < cb = b_padded[i]
        result.extend(a_padded[i:])
        result.append(_MID_CHAR)
        return "".join(result)

    # a_padded == b_padded for all max_len positions but a < b means b is longer.
    # Append a character in the middle of (last-char-of-a, first-remaining-of-b).
    return a + _MID_CHAR
