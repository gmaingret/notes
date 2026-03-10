---
phase: 04-attachments-comments-and-mobile
plan: "07"
subsystem: client-mobile
tags: [mobile, keyboard, testing, tdd]
requirements: [MOB-04, MOB-05]

dependency_graph:
  requires: [04-05]
  provides: [computeKeyboardOffset-pure-fn, keyboardOffset-tests-green, MOB-04-confirmed]
  affects: [client/src/components/DocumentView/FocusToolbar.tsx, client/src/test/keyboardOffset.test.ts]

tech_stack:
  added: []
  patterns: [visualViewport-keyboard-offset, pure-function-extraction, vi.mock-pdfjs-hoisting]

key_files:
  created: []
  modified:
    - client/src/components/DocumentView/FocusToolbar.tsx
    - client/src/test/keyboardOffset.test.ts
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/test/bulletTree.test.tsx

decisions:
  - computeKeyboardOffset exported from FocusToolbar.tsx (not a separate utils file) — keeps function co-located with its only consumer, test imports directly from component
  - MOB-04 requires no new code — dnd-kit PointerSensor + touchAction:none on dot was already in place from Phase 2

metrics:
  duration: 8min
  completed: 2026-03-09
  tasks_completed: 2
  files_modified: 4
---

# Phase 4 Plan 07: Mobile Keyboard Offset + MOB-04 Verification Summary

**One-liner:** Extracted `computeKeyboardOffset` pure function from FocusToolbar's visualViewport useEffect, made 3 keyboardOffset tests green, confirmed MOB-04 touch drag requires no code (dnd-kit PointerSensor + touchAction:none already in place).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extract computeKeyboardOffset + make tests green | 72a9aa5 | FocusToolbar.tsx, keyboardOffset.test.ts |
| 2 | MOB-04 verification + bulletTree DOMMatrix fix | b4b9352 | BulletNode.tsx, bulletTree.test.tsx |

## What Was Built

### Task 1: computeKeyboardOffset Pure Function

Extracted the inline keyboard offset calculation from FocusToolbar's visualViewport `update()` function into an exported pure function at the top of the file:

```typescript
export function computeKeyboardOffset(
  windowInnerHeight: number,
  vvOffsetTop: number,
  vvHeight: number,
): number {
  return Math.max(0, windowInnerHeight - vvOffsetTop - vvHeight);
}
```

The `useEffect` update function now calls `computeKeyboardOffset(window.innerHeight, vv.offsetTop, vv.height)` — no logic change, pure extraction.

Updated `keyboardOffset.test.ts` to:
- Replace the local placeholder stub with `import { computeKeyboardOffset } from '../components/DocumentView/FocusToolbar'`
- Add `vi.mock('pdfjs-dist')` to prevent transitive DOMMatrix crash (same pattern as pdfThumbnail.test.ts)
- Add 3rd test case: `computeKeyboardOffset(844, 0, 900)` returns 0 (negative clamped by Math.max)

All 3 tests GREEN.

### Task 2: MOB-04 Verification

Confirmed MOB-04 (touch-friendly drag handles) requires no new code:
- Dot div has `touchAction: 'none'` in its inline style (BulletNode.tsx line 241)
- BulletTree uses `PointerSensor` with `activationConstraint: { distance: 5 }`
- Added MOB-04 confirmation comment to the dot div

Also fixed the pre-existing bulletTree.test.tsx DOMMatrix crash (Rule 1 auto-fix) by adding the same `vi.mock('pdfjs-dist')` pattern.

## Verification Results

- TypeScript: CLEAN (`npx tsc --noEmit`)
- Vitest: 49/49 tests PASS across all 10 test files
- All Phase 4 client-side tests GREEN

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed bulletTree.test.tsx DOMMatrix crash in jsdom**
- **Found during:** Task 2 full test suite run
- **Issue:** bulletTree.test.tsx was failing with `ReferenceError: DOMMatrix is not defined` because BulletTree imports BulletNode which imports AttachmentRow which imports pdfjs-dist at module level — pdfjs-dist crashes in jsdom without a mock
- **Fix:** Added `vi.mock('pdfjs-dist', () => ({ getDocument: vi.fn(), GlobalWorkerOptions: { workerSrc: '' }, version: '4.0.0' }))` at top of bulletTree.test.tsx — identical pattern to pdfThumbnail.test.ts and keyboardOffset.test.ts
- **Files modified:** client/src/test/bulletTree.test.tsx
- **Commit:** b4b9352

## Self-Check: PASSED

All files found, all commits verified:
- FocusToolbar.tsx: FOUND
- keyboardOffset.test.ts: FOUND
- BulletNode.tsx: FOUND
- bulletTree.test.tsx: FOUND
- 04-07-SUMMARY.md: FOUND
- Commit 72a9aa5: FOUND
- Commit b4b9352: FOUND
