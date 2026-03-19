# Phase 22: Undo Coverage Extension - Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend the server-side undo/redo system to cover three currently-untracked mutations: toggling bullet complete/incomplete, editing bullet notes, and bulk-deleting completed bullets. No new UI — the existing Ctrl+Z/Ctrl+Y and undo status endpoint already handle replay. This is server-side only.

</domain>

<decisions>
## Implementation Decisions

### Mark-complete undo (UNDO-01)
- Add `recordUndoEvent` call inside `markComplete()` in `bulletService.ts`
- Forward op: `{ type: 'update_bullet', id, fields: { isComplete: newValue } }`
- Inverse op: `{ type: 'update_bullet', id, fields: { isComplete: !newValue } }`
- The `update_bullet` op type already handles `isComplete` in `applyOp()` — no schema changes needed
- Must wrap in a transaction so the mutation and undo event are atomic

### Note edit undo (UNDO-02)
- Add `recordUndoEvent` call inside the note-patching function in `bulletService.ts`
- Forward op: `{ type: 'update_bullet', id, fields: { content: newNote } }` — reuse `content` field since notes are stored as bullet content
- Inverse op: `{ type: 'update_bullet', id, fields: { content: previousNote } }`
- Must read the current note value BEFORE updating to capture the previous state
- Record on each save (blur), not per-keystroke — matches existing content save pattern
- NOTE: Check if notes use a separate `note` column vs `content`. If separate, the `update_bullet` applyOp needs to handle a `note` field too

### Bulk delete completed undo (UNDO-03)
- Currently does a hard `DELETE` — must change to soft delete (`SET deletedAt = NOW()`) for undo to work
- Before soft-deleting, snapshot all affected bullet IDs
- Record a SINGLE undo event with compound ops:
  - Forward op: array of `{ type: 'delete_bullet', id }` for each completed bullet
  - Inverse op: array of `{ type: 'restore_bullet', id }` for each completed bullet
- This means ONE Ctrl+Z restores ALL deleted completed bullets (not one per bullet)
- Requires extending `UndoOp` to support arrays OR adding a new `batch` op type
- Alternative: record N individual undo events in sequence — simpler but N Ctrl+Z presses needed (bad UX)

### UndoOp extension for batch operations
- Add a new op type: `{ type: 'batch', ops: UndoOp[] }` — applyOp loops through all ops
- This keeps the existing single-event-per-action model while supporting compound operations
- Alternatively, change forwardOp/inverseOp to `UndoOp | UndoOp[]` — applyOp checks `Array.isArray`

### No undo for collapse/expand
- Explicitly DO NOT add undo to `setCollapsed` — each expand/collapse would truncate the redo stack (redo-truncation on every `recordUndoEvent`), destroying the user's redo history for a trivial action

### Claude's Discretion
- Whether to use `batch` op type vs `UndoOp[]` array approach
- Whether the note field is `note` or `content` in the schema (must check)
- Transaction wrapping details for each mutation
- Whether bulk delete should remain hard delete with pre-snapshot or switch to soft delete

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Undo system
- `server/src/services/undoService.ts` — `UndoOp` type union, `recordUndoEvent()`, `applyOp()`, undo/redo functions
- `server/src/services/bulletService.ts` — `markComplete()` (line 419), note patching (line 472), existing `recordUndoEvent` calls in `patchBullet()`
- `server/src/routes/bullets.ts` — Bulk delete completed route (line 127), uses hard DELETE

### Schema
- `server/db/schema.ts` — bullets table schema, `note` column, `isComplete`, `deletedAt`
- `server/src/services/undoService.ts:7-13` — Current UndoOp discriminated union (must be extended for batch)

### Research
- `.planning/research/FEATURES.md` — Undo extension complexity analysis, bulk delete is HIGH complexity
- `.planning/research/PITFALLS.md` — Redo-truncation warning for collapse/expand, undo stack corruption risks

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `recordUndoEvent(dbInstance, userId, eventType, forwardOp, inverseOp)` — existing function, takes forward and inverse ops
- `update_bullet` op type already handles `isComplete`, `content`, `deletedAt` fields in `applyOp()`
- `restore_bullet` op type already restores soft-deleted bullets (sets `deletedAt = null`)
- `delete_bullet` op type already soft-deletes (sets `deletedAt = new Date()`)

### Established Patterns
- `patchBullet()` shows the pattern: read current state → build forward/inverse ops → `recordUndoEvent()` inside transaction
- Undo events use `schemaVersion: 1` and sequential `seq` numbering per user
- 50-event FIFO cap enforced automatically by `recordUndoEvent()`

### Integration Points
- `markComplete()` at line 419 — add `recordUndoEvent` call, wrap in transaction
- Note patching at line 472 — add `recordUndoEvent` call, read previous value first
- Bulk delete route at line 127 — change from hard DELETE to soft delete, add snapshot + `recordUndoEvent`
- `UndoOp` type at line 7 — extend with `batch` type for compound operations

### Key constraint
- `applyOp()` currently handles single ops only — must be extended to handle batch/array ops for bulk delete undo

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follow the existing `patchBullet` undo pattern for mark-complete and note edits. Bulk delete requires the most design work (batch op type).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 22-undo-coverage-extension*
*Context gathered: 2026-03-19*
