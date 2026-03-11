import { readFileSync } from 'fs';
import { resolve } from 'path';
import { describe, it, expect } from 'vitest';

// Vitest runs with cwd = client/ directory
const root = process.cwd();

const focusToolbarPath = resolve(root, 'src/components/DocumentView/FocusToolbar.tsx');
const bulletNodePath = resolve(root, 'src/components/DocumentView/BulletNode.tsx');
const sidebarPath = resolve(root, 'src/components/Sidebar/Sidebar.tsx');
const documentListPath = resolve(root, 'src/components/Sidebar/DocumentList.tsx');

const focusToolbar = readFileSync(focusToolbarPath, 'utf-8');
const bulletNode = readFileSync(bulletNodePath, 'utf-8');
const sidebar = readFileSync(sidebarPath, 'utf-8');
const documentList = readFileSync(documentListPath, 'utf-8');

describe('Phase 7.1 — FocusToolbar: no HTML entity characters', () => {
  it('FocusToolbar.tsx does NOT contain &#8677; (indent entity)', () => {
    expect(focusToolbar).not.toContain('&#8677;');
  });

  it('FocusToolbar.tsx does NOT contain &#8676; (outdent entity)', () => {
    expect(focusToolbar).not.toContain('&#8676;');
  });

  it('FocusToolbar.tsx does NOT contain &#8593; (arrow up entity)', () => {
    expect(focusToolbar).not.toContain('&#8593;');
  });

  it('FocusToolbar.tsx does NOT contain &#8595; (arrow down entity)', () => {
    expect(focusToolbar).not.toContain('&#8595;');
  });

  it('FocusToolbar.tsx does NOT contain &#8617; (undo entity)', () => {
    expect(focusToolbar).not.toContain('&#8617;');
  });

  it('FocusToolbar.tsx does NOT contain &#8618; (redo entity)', () => {
    expect(focusToolbar).not.toContain('&#8618;');
  });

  it('FocusToolbar.tsx does NOT contain &#128206; (attach entity)', () => {
    expect(focusToolbar).not.toContain('&#128206;');
  });

  it('FocusToolbar.tsx does NOT contain &#128172; (note entity)', () => {
    expect(focusToolbar).not.toContain('&#128172;');
  });

  it('FocusToolbar.tsx does NOT contain &#128278; (bookmark entity)', () => {
    expect(focusToolbar).not.toContain('&#128278;');
  });

  it('FocusToolbar.tsx does NOT contain &#10003; (check entity)', () => {
    expect(focusToolbar).not.toContain('&#10003;');
  });

  it('FocusToolbar.tsx does NOT contain &#128465; (delete entity)', () => {
    expect(focusToolbar).not.toContain('&#128465;');
  });

  it('FocusToolbar.tsx imports lucide-react (IndentIncrease and Undo2)', () => {
    expect(focusToolbar).toContain('IndentIncrease');
    expect(focusToolbar).toContain('Undo2');
  });
});

describe('Phase 7.1 — BulletNode: outer row has 2px vertical padding', () => {
  it('BulletNode.tsx outer row div has paddingTop: 2', () => {
    expect(bulletNode).toContain('paddingTop: 2');
  });

  it('BulletNode.tsx outer row div has paddingBottom: 2', () => {
    expect(bulletNode).toContain('paddingBottom: 2');
  });
});

describe('Phase 7.1 — Sidebar: footer replaces ... dropdown', () => {
  it('Sidebar.tsx does NOT contain showSidebarMenu state', () => {
    expect(sidebar).not.toContain('showSidebarMenu');
  });

  it('Sidebar.tsx does NOT contain dropdownStyle const', () => {
    expect(sidebar).not.toContain('dropdownStyle');
  });

  it('Sidebar.tsx imports Upload (footer export button)', () => {
    expect(sidebar).toContain('Upload');
  });

  it('Sidebar.tsx imports LogOut (footer logout button)', () => {
    expect(sidebar).toContain('LogOut');
  });

  it('Sidebar.tsx contains sidebar-footer-btn CSS class', () => {
    expect(sidebar).toContain('sidebar-footer-btn');
  });
});

describe('Phase 7.1 — DocumentList: accepts pendingRenameId prop', () => {
  it('DocumentList.tsx Props type contains pendingRenameId', () => {
    expect(documentList).toContain('pendingRenameId');
  });

  it('DocumentList.tsx passes initiallyRenaming to DocumentRow', () => {
    expect(documentList).toContain('initiallyRenaming');
  });
});
