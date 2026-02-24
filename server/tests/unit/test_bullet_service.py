"""Unit tests for bullet service — create, update, move, cascade delete."""

import uuid

import pytest
import pytest_asyncio

from app.db.database import get_test_db
from app.services.bullet_service import (
    create_bullet,
    delete_bullet,
    get_document_bullets,
    update_bullet,
)
from app.services.document_service import create_document


@pytest_asyncio.fixture
async def db():
    async with get_test_db() as database:
        yield database


@pytest_asyncio.fixture
async def doc_id(db):
    doc = await create_document(db, title="Test Doc", position="n")
    return doc.id


async def _make_bullet(
    db,
    doc_id: str,
    parent_id: str | None = None,
    content: str = "",
    position: str = "n",
) -> str:
    bid = str(uuid.uuid4())
    b = await create_bullet(
        db,
        bullet_id=bid,
        document_id=doc_id,
        parent_id=parent_id,
        content=content,
        position=position,
        is_complete=False,
    )
    return b.id


@pytest.mark.asyncio
async def test_create_bullet_returns_correct_fields(db, doc_id):
    bid = str(uuid.uuid4())
    bullet = await create_bullet(
        db,
        bullet_id=bid,
        document_id=doc_id,
        parent_id=None,
        content="Hello #world",
        position="n",
        is_complete=False,
    )
    assert bullet.id == bid
    assert bullet.document_id == doc_id
    assert bullet.parent_id is None
    assert bullet.content == "Hello #world"
    assert bullet.position == "n"
    assert bullet.is_complete is False
    assert bullet.created_at > 0
    assert bullet.updated_at > 0


@pytest.mark.asyncio
async def test_create_bullet_idempotent_on_duplicate_id(db, doc_id):
    """Calling create_bullet twice with the same id is idempotent."""
    bid = str(uuid.uuid4())
    b1 = await create_bullet(
        db, bullet_id=bid, document_id=doc_id, parent_id=None,
        content="first", position="n", is_complete=False,
    )
    b2 = await create_bullet(
        db, bullet_id=bid, document_id=doc_id, parent_id=None,
        content="different content", position="nn", is_complete=True,
    )
    # Second call returns the existing row unchanged.
    assert b1.id == b2.id
    assert b2.content == "first"


@pytest.mark.asyncio
async def test_update_bullet_content(db, doc_id):
    bid = await _make_bullet(db, doc_id, content="original")
    updated = await update_bullet(
        db, bullet_id=bid, content="updated", position=None,
        parent_id=None, is_complete=None,
    )
    assert updated is not None
    assert updated.content == "updated"


@pytest.mark.asyncio
async def test_update_bullet_is_complete(db, doc_id):
    bid = await _make_bullet(db, doc_id)
    updated = await update_bullet(
        db, bullet_id=bid, content=None, position=None,
        parent_id=None, is_complete=True,
    )
    assert updated is not None
    assert updated.is_complete is True


@pytest.mark.asyncio
async def test_update_bullet_position(db, doc_id):
    bid = await _make_bullet(db, doc_id, position="a")
    updated = await update_bullet(
        db, bullet_id=bid, content=None, position="z",
        parent_id=None, is_complete=None,
    )
    assert updated is not None
    assert updated.position == "z"


@pytest.mark.asyncio
async def test_update_bullet_move_to_new_parent(db, doc_id):
    parent_id = await _make_bullet(db, doc_id, position="a")
    child_id = await _make_bullet(db, doc_id, position="b")

    updated = await update_bullet(
        db, bullet_id=child_id, content=None, position=None,
        parent_id=parent_id, is_complete=None,
    )
    assert updated is not None
    assert updated.parent_id == parent_id


@pytest.mark.asyncio
async def test_update_bullet_clear_parent(db, doc_id):
    """Setting clear_parent=True should move bullet to root (parent_id = None)."""
    parent_id = await _make_bullet(db, doc_id, position="a")
    child_id = await _make_bullet(db, doc_id, parent_id=parent_id, position="b")

    updated = await update_bullet(
        db, bullet_id=child_id, content=None, position=None,
        parent_id=None, is_complete=None, clear_parent=True,
    )
    assert updated is not None
    assert updated.parent_id is None


@pytest.mark.asyncio
async def test_update_bullet_not_found_returns_none(db):
    result = await update_bullet(
        db, bullet_id="nonexistent-id", content="x", position=None,
        parent_id=None, is_complete=None,
    )
    assert result is None


@pytest.mark.asyncio
async def test_soft_delete_bullet(db, doc_id):
    bid = await _make_bullet(db, doc_id)
    deleted = await delete_bullet(db, bid)
    assert deleted is True

    bullets = await get_document_bullets(db, doc_id)
    assert bullets is not None
    assert not any(b.id == bid for b in bullets)


@pytest.mark.asyncio
async def test_soft_delete_not_found_returns_false(db):
    result = await delete_bullet(db, "nonexistent-id")
    assert result is False


@pytest.mark.asyncio
async def test_soft_delete_cascades_to_children(db, doc_id):
    """Deleting a parent soft-deletes all direct children."""
    parent_id = await _make_bullet(db, doc_id, position="a")
    child1_id = await _make_bullet(db, doc_id, parent_id=parent_id, position="b")
    child2_id = await _make_bullet(db, doc_id, parent_id=parent_id, position="c")

    deleted = await delete_bullet(db, parent_id)
    assert deleted is True

    bullets = await get_document_bullets(db, doc_id)
    assert bullets is not None
    ids_remaining = {b.id for b in bullets}
    assert parent_id not in ids_remaining
    assert child1_id not in ids_remaining
    assert child2_id not in ids_remaining


@pytest.mark.asyncio
async def test_soft_delete_cascades_three_levels(db, doc_id):
    """Cascade soft-delete through 3 levels of nesting."""
    level1 = await _make_bullet(db, doc_id, position="a")
    level2 = await _make_bullet(db, doc_id, parent_id=level1, position="b")
    level3 = await _make_bullet(db, doc_id, parent_id=level2, position="c")

    await delete_bullet(db, level1)

    bullets = await get_document_bullets(db, doc_id)
    assert bullets is not None
    ids_remaining = {b.id for b in bullets}
    assert level1 not in ids_remaining
    assert level2 not in ids_remaining
    assert level3 not in ids_remaining


@pytest.mark.asyncio
async def test_get_document_bullets_returns_none_for_unknown_doc(db):
    result = await get_document_bullets(db, "nonexistent-doc-id")
    assert result is None


@pytest.mark.asyncio
async def test_get_document_bullets_ordered_by_position(db, doc_id):
    """Bullets returned are sorted lexicographically by position."""
    await _make_bullet(db, doc_id, position="z")
    await _make_bullet(db, doc_id, position="a")
    await _make_bullet(db, doc_id, position="m")

    bullets = await get_document_bullets(db, doc_id)
    assert bullets is not None
    positions = [b.position for b in bullets]
    assert positions == sorted(positions)
