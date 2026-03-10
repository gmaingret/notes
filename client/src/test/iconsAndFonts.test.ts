import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';
import { describe, it, expect } from 'vitest';

// Vitest runs with cwd = client/ directory
const root = process.cwd();

const sidebarPath = resolve(root, 'src/components/Sidebar/Sidebar.tsx');
const documentRowPath = resolve(root, 'src/components/Sidebar/DocumentRow.tsx');
const documentViewPath = resolve(root, 'src/components/DocumentView/DocumentView.tsx');
const bulletNodePath = resolve(root, 'src/components/DocumentView/BulletNode.tsx');
const filteredBulletListPath = resolve(root, 'src/components/DocumentView/FilteredBulletList.tsx');
const attachmentRowPath = resolve(root, 'src/components/DocumentView/AttachmentRow.tsx');
const indexHtmlPath = resolve(root, 'index.html');
const mainTsxPath = resolve(root, 'src/main.tsx');
const indexCssPath = resolve(root, 'src/index.css');
const manifestPath = resolve(root, 'public/manifest.webmanifest');
const icon192Path = resolve(root, 'public/icon-192.png');
const icon512Path = resolve(root, 'public/icon-512.png');

const sidebar = readFileSync(sidebarPath, 'utf-8');
const documentRow = readFileSync(documentRowPath, 'utf-8');
const documentView = readFileSync(documentViewPath, 'utf-8');
const bulletNode = readFileSync(bulletNodePath, 'utf-8');
const filteredBulletList = readFileSync(filteredBulletListPath, 'utf-8');
const attachmentRow = readFileSync(attachmentRowPath, 'utf-8');
const indexHtml = readFileSync(indexHtmlPath, 'utf-8');
const mainTsx = readFileSync(mainTsxPath, 'utf-8');
const indexCss = readFileSync(indexCssPath, 'utf-8');

describe('VISL-01: Unicode/emoji icons replaced with Lucide React SVG components', () => {
  it('VISL-01: Sidebar.tsx does NOT contain ⋯ (ellipsis overflow button)', () => {
    expect(sidebar).not.toContain('⋯');
  });

  it('VISL-01: Sidebar.tsx does NOT contain ✕ (X close button)', () => {
    expect(sidebar).not.toContain('✕');
  });

  it('VISL-01: Sidebar.tsx does NOT contain 🔖 (bookmark tab emoji)', () => {
    expect(sidebar).not.toContain('🔖');
  });

  it('VISL-01: DocumentRow.tsx does NOT contain ⋯ (overflow menu button)', () => {
    expect(documentRow).not.toContain('⋯');
  });

  it('VISL-01: DocumentView.tsx does NOT contain &#9776; (hamburger HTML entity)', () => {
    expect(documentView).not.toContain('&#9776;');
  });

  it('VISL-01: BulletNode.tsx does NOT contain ▶ (collapse chevron)', () => {
    expect(bulletNode).not.toContain('▶');
  });

  it('VISL-01: BulletNode.tsx does NOT contain ✅ (swipe complete emoji)', () => {
    expect(bulletNode).not.toContain('✅');
  });

  it('VISL-01: BulletNode.tsx does NOT contain 🗑️ (swipe delete emoji)', () => {
    expect(bulletNode).not.toContain('🗑️');
  });

  it('VISL-01: BulletNode.tsx does NOT contain 🔖 (bookmark icon emoji)', () => {
    expect(bulletNode).not.toContain('🔖');
  });

  it('VISL-01: FilteredBulletList.tsx does NOT contain ★ (filled star)', () => {
    expect(filteredBulletList).not.toContain('★');
  });

  it('VISL-01: FilteredBulletList.tsx does NOT contain ☆ (empty star)', () => {
    expect(filteredBulletList).not.toContain('☆');
  });

  it('VISL-01: AttachmentRow.tsx does NOT contain 📎 (paperclip emoji)', () => {
    expect(attachmentRow).not.toContain('📎');
  });

  it('VISL-01: index.html references /favicon.svg (not /vite.svg)', () => {
    expect(indexHtml).toContain('favicon.svg');
    expect(indexHtml).not.toContain('vite.svg');
  });
});

describe('VISL-02: Inter variable font loaded from app server', () => {
  it('VISL-02: main.tsx imports @fontsource-variable/inter', () => {
    expect(mainTsx).toContain("@fontsource-variable/inter");
  });

  it("VISL-02: index.css body rule contains 'Inter Variable', sans-serif", () => {
    expect(indexCss).toContain("'Inter Variable', sans-serif");
  });
});

describe('VISL-03: JetBrains Mono variable font for code and chips', () => {
  it('VISL-03: main.tsx imports @fontsource-variable/jetbrains-mono', () => {
    expect(mainTsx).toContain("@fontsource-variable/jetbrains-mono");
  });

  it("VISL-03: index.css .chip rule contains 'JetBrains Mono Variable', monospace", () => {
    expect(indexCss).toContain("'JetBrains Mono Variable', monospace");
  });
});

describe('PWA-01: Valid PWA manifest with name, standalone display, and correct start URL', () => {
  it('PWA-01: index.html contains rel="manifest" link', () => {
    expect(indexHtml).toContain('rel="manifest"');
  });

  it('PWA-01: public/manifest.webmanifest file exists', () => {
    expect(existsSync(manifestPath)).toBe(true);
  });

  it('PWA-01: manifest.webmanifest contains "name": "Notes"', () => {
    const manifest = readFileSync(manifestPath, 'utf-8');
    expect(manifest).toContain('"name": "Notes"');
  });

  it('PWA-01: manifest.webmanifest contains "display": "standalone"', () => {
    const manifest = readFileSync(manifestPath, 'utf-8');
    expect(manifest).toContain('"display": "standalone"');
  });
});

describe('PWA-02: 192x192 and 512x512 PNG icon files exist in public/', () => {
  it('PWA-02: public/icon-192.png exists', () => {
    expect(existsSync(icon192Path)).toBe(true);
  });

  it('PWA-02: public/icon-512.png exists', () => {
    expect(existsSync(icon512Path)).toBe(true);
  });
});

describe('PWA-03: App opens in standalone mode when launched from home screen', () => {
  it('PWA-03: manifest.webmanifest display field is "standalone"', () => {
    const manifest = readFileSync(manifestPath, 'utf-8');
    expect(manifest).toContain('"display": "standalone"');
  });
});
