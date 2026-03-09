---
phase: 02-core-outliner
plan: "02"
subsystem: server-services
tags: [bullet-service, undo-service, tree-operations, drizzle-orm, tdd]
dependency_graph:
  requires: [02-01]
  provides: [bulletService, undoService]
  affects: [02-03, 02-04, 02-05]
tech_stack:
  added: []
  patterns:
    - FLOAT8 midpoint positioning (mirrors computeDocumentInsertPosition pattern)
    - Drizzle transaction wrapping every bullet mutation + undo event atomically
    - Discriminated union UndoOp type for type-safe forward/inverse ops
    - node:crypto randomUUID instead of uuid package (no new dependency added)
key_files:
  created:
    - server/src/services/undoService.ts
    - server/src/services/bulletService.ts
  modified:
    - server/tests/undo.test.ts
    - server/tests/bullets.test.ts
decisions:
  - Use node:crypto randomUUID instead of uuid package — uuid not in server package.json; randomUUID is built-in Node.js 14.17+ and avoids adding a new dependency
  - markComplete has no undo recording — BULL-12 explicitly excludes markComplete from undo requirements; setCollapsed does record undo per CONTEXT.md (structural op)
  - recordUndoEvent accepts a dbInstance parameter — caller must pass the active transaction handle so both the bullet mutation and undo event commit atomically
  - applyOp in undoService executes ops directly via Drizzle without calling bulletService — prevents circular dependency
metrics:
  duration: 4min
  completed_date: "2026-03-09"
  tasks: 2
  files: 4
---

# Phase 2 Plan 02: bulletService and undoService Summary

**One-liner:** Server-side bullet tree service (9 ops) and undo stack service (50-step FIFO) with every mutation recording forward/inverse op pairs in a single Drizzle transaction.

## What Was Built

### undoService.ts
- `recordUndoEvent(dbInstance, userId, eventType, forwardOp, inverseOp)` — inserts undo_event row, truncates redo stack (seq > currentSeq), enforces 50-step FIFO cap (seq < newSeq-50), upserts undo_cursor. Must be called inside an active transaction.
- `undo(dbInstance, userId)` — fetches event at currentSeq, applies inverseOp, decrements cursor.
- `redo(dbInstance, userId)` — fetches event at currentSeq+1, applies forwardOp, increments cursor.
- `getStatus(dbInstance, userId)` — returns `{ canUndo, canRedo }` based on cursor vs event range.
- `UndoOp` discriminated union: `create_bullet | delete_bullet | restore_bullet | restore_bullet_delete | update_bullet | move_bullet`
- `applyOp` (internal) — dispatches on UndoOp type and executes directly via Drizzle, no bulletService import.

### bulletService.ts
- `computeBulletInsertPosition(dbInstance, documentId, parentId, afterId)` — FLOAT8 midpoint algorithm; mirrors computeDocumentInsertPosition from documentService.ts exactly.
- `createBullet(userId, { documentId, parentId, afterId, content })` — generates UUID via node:crypto, inserts bullet, records undo in same tx.
- `indentBullet(userId, bulletId)` — finds previous sibling, appends bullet as last child of it; no-op if first sibling.
- `outdentBullet(userId, bulletId)` — reparents bullet to grandparent, positioned after parent; no-op at root.
- `moveBullet(userId, bulletId, { newParentId, afterId })` — cycle guard via getDescendantIds traversal; throws if newParentId is a descendant.
- `softDeleteBullet(userId, bulletId)` — sets deletedAt=now(); inverseOp=restore_bullet.
- `markComplete(userId, bulletId, isComplete)` — simple UPDATE, no undo recording.
- `setCollapsed(userId, bulletId, isCollapsed)` — UPDATE + undo recording (structural op per CONTEXT.md).
- `getDocumentBullets(userId, documentId)` — returns all non-deleted bullets ordered by position.

## Test Results

| Test File | Tests | Status |
|-----------|-------|--------|
| tests/undo.test.ts | 10 | GREEN |
| tests/bullets.test.ts | 22 | GREEN |
| **Total** | **32** | **GREEN** |

All tests use mocked Drizzle DB (vi.mock pattern consistent with auth.test.ts). No real DB connection needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] uuid package not available in server**
- **Found during:** Task 2
- **Issue:** The plan specified `import { v4 as uuidv4 } from 'uuid'` but `uuid` is not in `server/package.json`. Test immediately failed with "Cannot find package 'uuid'".
- **Fix:** Replaced with `import { randomUUID } from 'node:crypto'` — built-in Node.js, no new dependency, UUID v4 compatible.
- **Files modified:** `server/src/services/bulletService.ts`
- **Commit:** eb4c382

## Key Decisions

| Decision | Rationale |
|----------|-----------|
| `node:crypto randomUUID` instead of `uuid` | No new dependency; built-in since Node 14.17+; UUID v4 compliant |
| `markComplete` has no undo | Explicit BULL-12 requirement — only structural ops get undo |
| `recordUndoEvent` takes `dbInstance` param | Allows callers to pass their active transaction handle for atomicity |
| `applyOp` never imports `bulletService` | Prevents circular dependency; ops execute raw Drizzle calls |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| server/src/services/undoService.ts | FOUND |
| server/src/services/bulletService.ts | FOUND |
| .planning/phases/02-core-outliner/02-02-SUMMARY.md | FOUND |
| commit 78b5ee2 (undoService) | FOUND |
| commit eb4c382 (bulletService) | FOUND |
