/**
 * Pure cursor/DOM helper functions for contenteditable bullet editing.
 * These functions are framework-free (no React imports) so they can be unit-tested directly.
 */

/**
 * Returns true if the cursor (or selection anchor) is at the very start of the element.
 */
export function isCursorAtStart(el: HTMLDivElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return false;
  const range = sel.getRangeAt(0);
  return (
    range.collapsed &&
    range.startOffset === 0 &&
    (range.startContainer === el || range.startContainer === el.firstChild)
  );
}

/**
 * Returns true if the cursor is at the very end of the element's text content.
 */
export function isCursorAtEnd(el: HTMLDivElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return false;
  const range = sel.getRangeAt(0);
  if (!range.collapsed) return false;
  if (range.startContainer === el) {
    return range.startOffset === el.childNodes.length;
  }
  if (range.startContainer === el.lastChild) {
    const node = range.startContainer;
    return range.startOffset === (node.textContent?.length ?? 0);
  }
  return false;
}

/**
 * Splits the element's text at the current cursor position.
 * Returns { before, after } where before is text before the cursor and after is text after.
 * If no selection exists, returns the full text as `before` and empty string as `after`.
 */
export function splitAtCursor(el: HTMLDivElement): { before: string; after: string } {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    return { before: el.textContent ?? '', after: '' };
  }
  const range = sel.getRangeAt(0);
  const beforeRange = document.createRange();
  beforeRange.setStart(el, 0);
  beforeRange.setEnd(range.startContainer, range.startOffset);
  const before = beforeRange.toString();
  const after = (el.textContent ?? '').slice(before.length);
  return { before, after };
}

/**
 * Sets the cursor inside a contenteditable div at the given character offset.
 * Clamps the offset to the text length if out of range.
 * Sets contentEditable to 'true' and focuses the element before placing the cursor,
 * so the mobile keyboard stays open when focus moves between bullets programmatically.
 */
export function setCursorAtPosition(el: HTMLDivElement, offset: number): void {
  // Set contentEditable before focus so the mobile keyboard stays open when
  // focus moves from one bullet to another programmatically.
  el.contentEditable = 'true';
  el.focus();
  const textNode = el.firstChild;
  if (!textNode) return;
  const range = document.createRange();
  const safeOffset = Math.min(offset, textNode.textContent?.length ?? 0);
  range.setStart(textNode, safeOffset);
  range.collapse(true);
  const sel = window.getSelection();
  if (sel) {
    sel.removeAllRanges();
    sel.addRange(range);
  }
}

/**
 * Places the cursor at the end of the element's text content.
 */
export function placeCursorAtEnd(el: HTMLDivElement): void {
  const range = document.createRange();
  const sel = window.getSelection();
  range.selectNodeContents(el);
  range.collapse(false);
  sel?.removeAllRanges();
  sel?.addRange(range);
}
