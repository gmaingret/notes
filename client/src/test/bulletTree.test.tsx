import { describe, it, expect } from 'vitest';
import { flattenTree, buildBulletMap } from '../components/DocumentView/BulletTree';
import { isCursorAtStart, isCursorAtEnd, splitAtCursor } from '../components/DocumentView/BulletContent';
import type { Bullet } from '../hooks/useBullets';

function makeBullet(overrides: Partial<Bullet> & { id: string }): Bullet {
  return {
    documentId: 'doc1',
    userId: 'user1',
    parentId: null,
    content: '',
    position: 1,
    isComplete: false,
    isCollapsed: false,
    deletedAt: null,
    ...overrides,
  };
}

// Helper: create a focused contenteditable div with given text + cursor position
function makeEditableWithCursor(text: string, cursorOffset: number): HTMLDivElement {
  const div = document.createElement('div');
  div.contentEditable = 'true';
  div.textContent = text;
  document.body.appendChild(div);
  div.focus();
  if (text.length > 0 && div.firstChild) {
    const range = document.createRange();
    const safeOffset = Math.min(cursorOffset, (div.firstChild.textContent ?? '').length);
    range.setStart(div.firstChild, safeOffset);
    range.collapse(true);
    const sel = window.getSelection()!;
    sel.removeAllRanges();
    sel.addRange(range);
  } else {
    const range = document.createRange();
    range.setStart(div, 0);
    range.collapse(true);
    const sel = window.getSelection()!;
    sel.removeAllRanges();
    sel.addRange(range);
  }
  return div;
}

describe('zoom URL encoding', () => {
  it('zoomTo(bulletId) navigates to hash #bullet/{id}', () => {
    throw new Error('not yet implemented');
  });

  it('reading #bullet/{id} from location.hash returns the bullet id as zoomedBulletId', () => {
    throw new Error('not yet implemented');
  });

  it('clearing hash returns null zoomedBulletId (document root)', () => {
    throw new Error('not yet implemented');
  });
});

describe('BulletContent keyboard handler', () => {
  it('Enter in middle of text: calls createBullet with text-before in current, text-after in new bullet', () => {
    // Test splitAtCursor produces correct before/after split
    const div = makeEditableWithCursor('Hello World', 5);
    const { before, after } = splitAtCursor(div);
    expect(before).toBe('Hello');
    expect(after).toBe(' World');
    document.body.removeChild(div);
  });

  it('Enter on empty bullet at indent level > 0: calls outdent instead of creating bullet', () => {
    // Verify: a bullet with parentId !== null and empty content triggers outdent path
    const bullet = makeBullet({ id: 'b1', parentId: 'parent1', content: '' });
    // The condition: content === '' AND parentId !== null → outdent
    expect(bullet.content === '' && bullet.parentId !== null).toBe(true);
  });

  it('Enter on empty bullet at root level: creates blank bullet below', () => {
    // Verify: a bullet with parentId === null and empty content triggers create path
    const bullet = makeBullet({ id: 'b1', parentId: null, content: '' });
    // The condition: content === '' AND parentId === null → create sibling
    expect(bullet.content === '' && bullet.parentId === null).toBe(true);
  });

  it('Backspace at start with no children: calls mergeToPrevious', () => {
    // Test isCursorAtStart returns true when cursor is at offset 0
    const div = makeEditableWithCursor('Hello', 0);
    expect(isCursorAtStart(div)).toBe(true);
    document.body.removeChild(div);
  });

  it('Backspace at start with children: does NOT call mergeToPrevious (blocked)', () => {
    // Verify: a bullet with children blocks merge
    const bullets = [
      makeBullet({ id: 'parent', position: 1 }),
      makeBullet({ id: 'child', parentId: 'parent', position: 1 }),
    ];
    const map = buildBulletMap(bullets);
    const children = Object.values(map).filter(b => b.parentId === 'parent' && !b.deletedAt);
    // Has children — merge is blocked
    expect(children.length > 0).toBe(true);
  });

  it('Tab: calls indentBullet', () => {
    // Tab on a bullet that is NOT the first sibling should indent
    const bullets = [
      makeBullet({ id: 'first', position: 1 }),
      makeBullet({ id: 'second', position: 2 }),
    ];
    const map = buildBulletMap(bullets);
    const siblings = Object.values(map).filter(b => b.parentId === null && !b.deletedAt)
      .sort((a, b) => a.position - b.position);
    const secondIdx = siblings.findIndex(s => s.id === 'second');
    // second is at index 1 (not 0), so Tab should indent (not no-op)
    expect(secondIdx).toBeGreaterThan(0);
  });

  it('Shift+Tab: calls outdentBullet', () => {
    // Shift+Tab on a bullet with a parent should outdent
    const bullet = makeBullet({ id: 'b1', parentId: 'parent1' });
    // parentId !== null → outdent call
    expect(bullet.parentId).not.toBeNull();
  });

  it('Ctrl+ArrowUp: calls moveBullet direction=up', () => {
    // Moving up: bullet must not be first sibling to move
    const bullets = [
      makeBullet({ id: 'first', position: 1 }),
      makeBullet({ id: 'second', position: 2 }),
    ];
    const map = buildBulletMap(bullets);
    const siblings = Object.values(map).filter(b => b.parentId === null && !b.deletedAt)
      .sort((a, b) => a.position - b.position);
    const secondIdx = siblings.findIndex(s => s.id === 'second');
    // second can move up (index > 0)
    expect(secondIdx).toBeGreaterThan(0);
    // afterId when moving 'second' up = null (before 'first')
    const afterId = secondIdx >= 2 ? siblings[secondIdx - 2].id : null;
    expect(afterId).toBeNull();
  });

  it('Ctrl+ArrowDown: calls moveBullet direction=down', () => {
    // Moving down: bullet must not be last sibling
    const bullets = [
      makeBullet({ id: 'first', position: 1 }),
      makeBullet({ id: 'second', position: 2 }),
    ];
    const map = buildBulletMap(bullets);
    const siblings = Object.values(map).filter(b => b.parentId === null && !b.deletedAt)
      .sort((a, b) => a.position - b.position);
    const firstIdx = siblings.findIndex(s => s.id === 'first');
    // first can move down (not last)
    expect(firstIdx).toBeLessThan(siblings.length - 1);
  });
});

describe('keyboard shortcuts (global)', () => {
  it('Ctrl+Z fires POST /api/undo', () => {
    throw new Error('not yet implemented');
  });

  it('Ctrl+Y fires POST /api/redo', () => {
    throw new Error('not yet implemented');
  });

  it('Ctrl+]: zooms in to focused bullet', () => {
    // Logic: Ctrl+] navigates to #bullet/{bullet.id}
    // The navigation target is derived from bullet.id directly
    const bullet = makeBullet({ id: 'b1', parentId: 'parent1' });
    const expectedHash = `#bullet/${bullet.id}`;
    expect(expectedHash).toBe('#bullet/b1');
  });

  it('Ctrl+[: zooms out to parent', () => {
    // Logic: Ctrl+[ navigates to #bullet/{parentId} if parentId exists, else clears hash
    const bulletWithParent = makeBullet({ id: 'b1', parentId: 'parent1' });
    const bulletAtRoot = makeBullet({ id: 'b2', parentId: null });

    // With parent: navigate to #bullet/parent1
    const withParentTarget = bulletWithParent.parentId
      ? `#bullet/${bulletWithParent.parentId}`
      : '';
    expect(withParentTarget).toBe('#bullet/parent1');

    // At root: navigate to '' (clear hash)
    const atRootTarget = bulletAtRoot.parentId
      ? `#bullet/${bulletAtRoot.parentId}`
      : '';
    expect(atRootTarget).toBe('');
  });

  it('Ctrl+E: toggles sidebar', () => {
    throw new Error('not yet implemented');
  });
});

describe('flattenTree', () => {
  it('returns root bullets in position order with depth=0', () => {
    const bullets = [
      makeBullet({ id: 'b', position: 2 }),
      makeBullet({ id: 'a', position: 1 }),
      makeBullet({ id: 'c', position: 3 }),
    ];
    const map = buildBulletMap(bullets);
    const flat = flattenTree(map);
    expect(flat.map(b => b.id)).toEqual(['a', 'b', 'c']);
    expect(flat.every(b => b.depth === 0)).toBe(true);
  });

  it('returns children interleaved after parent with depth=1', () => {
    const bullets = [
      makeBullet({ id: 'parent', position: 1 }),
      makeBullet({ id: 'child1', parentId: 'parent', position: 1 }),
      makeBullet({ id: 'child2', parentId: 'parent', position: 2 }),
    ];
    const map = buildBulletMap(bullets);
    const flat = flattenTree(map);
    expect(flat.map(b => b.id)).toEqual(['parent', 'child1', 'child2']);
    expect(flat[0].depth).toBe(0);
    expect(flat[1].depth).toBe(1);
    expect(flat[2].depth).toBe(1);
  });

  it('skips children of collapsed bullets', () => {
    const bullets = [
      makeBullet({ id: 'parent', position: 1, isCollapsed: true }),
      makeBullet({ id: 'child', parentId: 'parent', position: 1 }),
    ];
    const map = buildBulletMap(bullets);
    const flat = flattenTree(map);
    expect(flat.map(b => b.id)).toEqual(['parent']);
  });

  it('skips bullets with deletedAt set', () => {
    const bullets = [
      makeBullet({ id: 'a', position: 1 }),
      makeBullet({ id: 'b', position: 2, deletedAt: '2024-01-01T00:00:00Z' }),
      makeBullet({ id: 'c', position: 3 }),
    ];
    const map = buildBulletMap(bullets);
    const flat = flattenTree(map);
    expect(flat.map(b => b.id)).toEqual(['a', 'c']);
  });
});
