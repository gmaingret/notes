import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve } from 'path';

// Vitest runs with cwd = client/ directory
// client/index.html is at the root of client/
const indexHtmlPath = resolve(process.cwd(), 'index.html');
// client/src/index.css
const indexCssPath = resolve(process.cwd(), 'src/index.css');

const indexHtml = readFileSync(indexHtmlPath, 'utf-8');
const indexCss = readFileSync(indexCssPath, 'utf-8');

describe('Dark Mode', () => {
  it('DRKM-04: index.html contains color-scheme meta tag with name attribute', () => {
    expect(indexHtml).toContain('name="color-scheme"');
  });

  it('DRKM-04: index.html contains color-scheme meta tag with content="light dark"', () => {
    expect(indexHtml).toContain('content="light dark"');
  });

  it('DRKM-03: index.html contains FOUC script that adds dark class', () => {
    expect(indexHtml).toContain("document.documentElement.classList.add('dark')");
  });

  it('DRKM-03: index.html contains FOUC script that checks prefers-color-scheme', () => {
    expect(indexHtml).toContain("window.matchMedia('(prefers-color-scheme: dark)').matches");
  });

  it('DRKM-01: index.css contains @media (prefers-color-scheme: dark) block', () => {
    expect(indexCss).toContain('@media (prefers-color-scheme: dark)');
  });

  it('DRKM-01: index.css contains --color-bg-base token', () => {
    expect(indexCss).toContain('--color-bg-base');
  });
});
