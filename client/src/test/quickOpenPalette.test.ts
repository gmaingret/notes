import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';
import { describe, it, expect } from 'vitest';

const root = process.cwd();

const uiStorePath = resolve(root, 'src/store/uiStore.ts');
const appPagePath = resolve(root, 'src/pages/AppPage.tsx');
const palettePath = resolve(root, 'src/components/DocumentView/QuickOpenPalette.tsx');

const uiStore = readFileSync(uiStorePath, 'utf-8');
const appPage = readFileSync(appPagePath, 'utf-8');
const palette = existsSync(palettePath) ? readFileSync(palettePath, 'utf-8') : '';

describe('Phase 8 — QKOP-01: palette state and Ctrl+K listener', () => {
  it('uiStore contains quickOpenOpen boolean', () => {
    expect(uiStore).toContain('quickOpenOpen');
  });
  it('uiStore contains setQuickOpenOpen action', () => {
    expect(uiStore).toContain('setQuickOpenOpen');
  });
  it('AppPage.tsx listens for Ctrl+Shift+K / Cmd+Shift+K', () => {
    expect(appPage).toContain("key === 'K'");
    expect(appPage).toContain('shiftKey');
  });
});

describe('Phase 8 — QKOP-02: recent documents empty state', () => {
  it('QuickOpenPalette sorts by lastOpenedAt', () => {
    expect(palette).toContain('lastOpenedAt');
  });
});

describe('Phase 8 — QKOP-03: client-side document title search', () => {
  it('QuickOpenPalette uses case-insensitive substring match', () => {
    expect(palette).toMatch(/toLowerCase\(\)\.includes/);
  });
});

describe('Phase 8 — QKOP-04: bullet content search via useSearch', () => {
  it('QuickOpenPalette uses useSearch hook', () => {
    expect(palette).toContain('useSearch');
  });
});

describe('Phase 8 — QKOP-05: bookmarks in palette results', () => {
  it('QuickOpenPalette uses useBookmarks hook', () => {
    expect(palette).toContain('useBookmarks');
  });
});

describe('Phase 8 — QKOP-06: keyboard navigation', () => {
  it('QuickOpenPalette handles ArrowDown key', () => {
    expect(palette).toContain('ArrowDown');
  });
  it('QuickOpenPalette handles ArrowUp key', () => {
    expect(palette).toContain('ArrowUp');
  });
  it('QuickOpenPalette handles Enter key to open result', () => {
    expect(palette).toContain('Enter');
  });
});

describe('Phase 8 — QKOP-07: close on Escape and click-outside', () => {
  it('QuickOpenPalette handles Escape key', () => {
    expect(palette).toContain('Escape');
  });
  it('QuickOpenPalette has backdrop with onClose click handler', () => {
    expect(palette).toContain('onClose');
  });
});
