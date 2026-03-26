/**
 * Shared bullet tree operations used by both useKeyboardHandlers (desktop keyboard)
 * and FocusToolbar (mobile toolbar). Single source of truth for move/indent/outdent logic.
 */
import type { Bullet } from '../../hooks/useBullets';
import type { BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';

/** Cached DOM selector for bullet contenteditable divs. */
const BULLET_SELECTOR = '[id^="bullet-"]:not([id^="bullet-row-"])';

export function getAllBulletElements(): HTMLDivElement[] {
  return Array.from(document.querySelectorAll<HTMLDivElement>(BULLET_SELECTOR));
}

/** Compute afterId for moving a bullet up among its siblings. Returns 'noop' to signal no-op. */
export function computeMoveUpAfterId(
  bulletMap: BulletMap,
  bullet: Bullet
): string | null | 'noop' {
  const siblings = getChildren(bulletMap, bullet.parentId);
  const idx = siblings.findIndex(s => s.id === bullet.id);
  if (idx <= 0) return 'noop';
  return idx >= 2 ? siblings[idx - 2].id : null;
}

/** Compute afterId for moving a bullet down among its siblings. Returns 'noop' to signal no-op. */
export function computeMoveDownAfterId(
  bulletMap: BulletMap,
  bullet: Bullet
): string | 'noop' {
  const siblings = getChildren(bulletMap, bullet.parentId);
  const idx = siblings.findIndex(s => s.id === bullet.id);
  if (idx >= siblings.length - 1) return 'noop';
  const nextNext = siblings[idx + 2] ?? null;
  return nextNext ? nextNext.id : siblings[idx + 1].id;
}

/** Check if indent is possible (bullet is not the first sibling). */
export function canIndent(bulletMap: BulletMap, bullet: Bullet): boolean {
  const siblings = getChildren(bulletMap, bullet.parentId);
  const idx = siblings.findIndex(s => s.id === bullet.id);
  return idx > 0;
}

/** Walk from a sibling to its deepest visible last-child (for Backspace merge target). */
export function findDeepestVisibleChild(
  bulletMap: BulletMap,
  startBullet: Bullet
): Bullet {
  let candidate = startBullet;
  while (true) {
    if (candidate.isCollapsed) break;
    const children = getChildren(bulletMap, candidate.id).filter(b => !b.deletedAt);
    if (children.length === 0) break;
    candidate = children[children.length - 1];
  }
  return candidate;
}
