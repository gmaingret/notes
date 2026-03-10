---
phase: 04-attachments-comments-and-mobile
plan: "09"
subsystem: client
tags: [gap-closure, custom-events, attachments, notes, react-hooks]
dependency_graph:
  requires: []
  provides: [focus-note-listener, attach-file-listener, noteVisible-state]
  affects: [ATT-02, CMT-02, CMT-03]
tech_stack:
  added: []
  patterns: [CustomEvent-listener via useEffect, hidden-file-input-pattern]
key_files:
  created: []
  modified:
    - client/src/components/DocumentView/BulletNode.tsx
decisions:
  - "focusOnMount set to `noteVisible && bullet.note === null` — focuses NoteRow only on new creation, not when already has a note"
  - "Two separate useEffects for focus-note and attach-file events, each filtered by bullet.id to scope per-bullet"
  - "Hidden file input placed inside !isDragOverlay guard — keeps drag overlay rendering clean"
metrics:
  duration: 8min
  completed_date: "2026-03-10"
  tasks_completed: 3
  files_modified: 1
---

# Phase 4 Plan 9: Wire CustomEvent Listeners in BulletNode Summary

**One-liner:** Added useEffect listeners for focus-note and attach-file CustomEvents in BulletNode, wiring FocusToolbar/ContextMenu Note and Attach-file actions that previously dispatched events with no listener on the receiving end.

## What Was Built

BulletNode.tsx received four targeted changes:

1. `useEffect` added to React imports
2. `useUploadAttachment` added to useAttachments import
3. Two useEffect listeners registered on document (filtered by `bullet.id`):
   - `focus-note` → sets `noteVisible = true`, causing NoteRow to render and focus
   - `attach-file` → clicks a hidden `<input type="file">` ref for that bullet
4. Hidden file input wired to `uploadAttachment.mutate({ bulletId, file })`
5. NoteRow render condition updated from `bullet.note !== null` to `bullet.note !== null || noteVisible`
6. NoteRow receives `focusOnMount={noteVisible && bullet.note === null}` for auto-focus on creation

## Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1+2 | Wire CustomEvent listeners + deploy | 48a0689 | client/src/components/DocumentView/BulletNode.tsx |

## Deviations from Plan

None — plan executed exactly as written.

## Auto-approved Checkpoint

Task 3 (checkpoint:human-verify) auto-approved per `auto_advance: true` config.
- App deployed to https://notes.gregorymaingret.fr
- Branch: phase-4/gap-closure-event-wiring
- Docker container rebuilt and running (confirmed "Server running on :3000")
- All 49 client unit tests pass

## Self-Check: PASSED

- [x] `client/src/components/DocumentView/BulletNode.tsx` — modified (confirmed)
- [x] Commit 48a0689 — exists on branch phase-4/gap-closure-event-wiring
- [x] TypeScript compiles clean (npx tsc --noEmit returned 0)
- [x] 49/49 vitest tests pass
- [x] Docker container running on server
