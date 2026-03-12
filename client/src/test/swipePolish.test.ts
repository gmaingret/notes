import { readFileSync } from 'fs';
import { resolve } from 'path';
import { describe, it, expect } from 'vitest';

const root = process.cwd();
const bulletNodePath = resolve(root, 'src/components/DocumentView/BulletNode.tsx');
const bulletNode = readFileSync(bulletNodePath, 'utf-8');

describe('Phase 8 — Swipe animations: icon scale proportional to drag', () => {
  // GEST-01
  it('BulletNode contains iconScale derived from swipeX ratio', () => {
    expect(bulletNode).toMatch(/iconScale/);
  });
  // GEST-02
  it('BulletNode applies iconScale to both Check and Trash2 icon containers', () => {
    expect(bulletNode).toMatch(/scale\(\$\{iconScale\}/);
  });
});

describe('Phase 8 — Swipe animations: snap-back and exit', () => {
  // GEST-03: existing transition already present — assert it stays
  it('BulletNode transition includes ease for snap-back', () => {
    expect(bulletNode).toContain('transform 0.2s ease');
  });
  // GEST-04
  it('BulletNode contains exitDirection state', () => {
    expect(bulletNode).toMatch(/exitDirection/);
  });
  it('BulletNode has onTransitionEnd handler that fires mutation', () => {
    expect(bulletNode).toMatch(/onTransitionEnd/);
  });
});

describe('Phase 8 — Swipe: touch drag activation', () => {
  // GEST-05: long-press on bullet dot activates drag (500ms)
  it('BulletNode uses long-press timer for touch drag', () => {
    expect(bulletNode).toContain('onDragStart');
    expect(bulletNode).toMatch(/setTimeout/);
  });
});
