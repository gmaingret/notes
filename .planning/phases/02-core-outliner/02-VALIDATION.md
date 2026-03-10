---
phase: 2
slug: core-outliner
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-09
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest 4.0.x (client) / vitest 3.0.x (server) |
| **Client config** | `client/vite.config.ts` — jsdom environment, setupFiles: src/test/setup.ts |
| **Server config** | `server/vitest.config.ts` — node environment |
| **Quick run command** | `cd server && npx vitest run tests/bullets.test.ts` |
| **Full suite command** | `cd server && npx vitest run && cd ../client && npx vitest run` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd server && npx vitest run tests/bullets.test.ts`
- **After every plan wave:** Run `cd server && npx vitest run && cd ../client && npx vitest run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 2-W0-01 | Wave0 | 0 | BULL-01..12 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-W0-02 | Wave0 | 0 | UNDO-01..04 | unit | `cd server && npx vitest run tests/undo.test.ts` | ❌ W0 | ⬜ pending |
| 2-W0-03 | Wave0 | 0 | BULL-07, KB-01..07 | unit (client) | `cd client && npx vitest run` | ❌ W0 | ⬜ pending |
| 2-BULL-01 | bullets | 1 | BULL-01 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-BULL-02 | bullets | 1 | BULL-02 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-BULL-03 | bullets | 1 | BULL-03 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-BULL-04 | bullets | 1 | BULL-04 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-BULL-05 | dnd | 2 | BULL-05 | unit (service) | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-BULL-06 | collapse | 2 | BULL-06 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-BULL-07 | zoom | 2 | BULL-07 | unit (client) | `cd client && npx vitest run` | ❌ W0 | ⬜ pending |
| 2-BULL-08 | bullets | 1 | BULL-08 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-BULL-11 | bullets | 1 | BULL-11 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-BULL-12 | bullets | 1 | BULL-12 | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ W0 | ⬜ pending |
| 2-KB-01..07 | keyboard | 1 | KB-01..07 | unit (client) | `cd client && npx vitest run` | ❌ W0 | ⬜ pending |
| 2-UNDO-01 | undo | 2 | UNDO-01 | unit | `cd server && npx vitest run tests/undo.test.ts` | ❌ W0 | ⬜ pending |
| 2-UNDO-02 | undo | 2 | UNDO-02 | unit | `cd server && npx vitest run tests/undo.test.ts` | ❌ W0 | ⬜ pending |
| 2-UNDO-03 | undo | 2 | UNDO-03 | unit | `cd server && npx vitest run tests/undo.test.ts` | ❌ W0 | ⬜ pending |
| 2-UNDO-04 | undo | 2 | UNDO-04 | unit | `cd server && npx vitest run tests/undo.test.ts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `server/tests/bullets.test.ts` — stubs for BULL-01 through BULL-12 service functions (position computation, indent/outdent logic, soft delete)
- [ ] `server/tests/undo.test.ts` — stubs for UNDO-01 through UNDO-04 (recordUndoEvent, undo, redo, 50-step cap)
- [ ] `client/src/test/bulletTree.test.tsx` — stubs for BULL-07 (URL zoom), KB-01..07 shortcuts, BulletContent keyboard handler split/merge logic
- [ ] `server/tests/helpers/` — bullet-specific fixtures for tree setup

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| iOS Safari Enter creates new bullet | KB-01 | Programmatic focus in same keydown event; requires real device | On iOS Safari, open a note, press Enter in a bullet — verify new bullet created and focused |
| Drag-and-drop touch reorder | BULL-05 | Touch pointer events differ from mouse | On touch device, long-press a bullet, drag to new position, verify tree updated correctly |
| Collapsed state survives page refresh | BULL-06 | Browser persistence; not unit-testable | Collapse a branch, refresh page, verify branch remains collapsed |
| Breadcrumb keyboard navigation | BULL-08 | Complex keyboard + DOM interaction | Zoom into bullet (click), press Escape or breadcrumb, verify parent view restored |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
