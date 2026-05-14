---
gsd_state_version: 1.0
milestone: v2.3
milestone_name: Robustness & Quality
status: ready_to_deploy
stopped_at: Phase 19 + 23 complete, awaiting prod deploy
last_updated: "2026-05-14T11:36:00Z"
progress:
  total_phases: 5
  completed_phases: 5
  total_plans: 9
  completed_plans: 9
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-19)

**Core value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.
**Current focus:** v2.3 milestone — all phases complete locally, awaiting prod deploy of Phase 19 (Phase 23 already merged to `main` via PR #47).

## Current Position

Phase: 19 (server-foundation) — READY TO DEPLOY (branch `phase-19/server-foundation`)
Phase: 23 (component-refactoring) — ALREADY MERGED to main via PR #47 (planning state updated 2026-05-14)

## Performance Metrics

**Velocity (all milestones):**

| Milestone | Phases | Plans | Days | Plans/Day |
|-----------|--------|-------|------|-----------|
| v1.0 MVP | 4 | 32 | 2 | 16 |
| v1.1 Mobile & UI Polish | 5 | 23 | 2 | 11.5 |
| v2.0 Native Android | 4 | 17 | 3 | 5.7 |
| v2.1 Widget | 3 | 8 | 1 | 8 |
| v2.2 Security Hardening | 3 | 5 | 1 | 5 |
| v2.3 Robustness & Quality | 5 | 9 | — | — |
| **Total** | **24** | **94** | — | — |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.

Recent decisions affecting current work:

- v2.3 uses `sonner@2.0.7` for toast notifications (keep distinct from existing UndoToast component)
- v2.3 uses `react-error-boundary@6.1.1` for declarative error boundaries with `resetKeys`
- CI workflows must be validation-only — no SSH deploy step (would fill 30GB server disk)
- Token refresh interceptor must use shared promise lock to prevent concurrent refresh race condition
- `_isRetry` flag required on retry to prevent infinite 401 loops (reference: Android commit `0457017`)
- [Phase 19]: Pre-check undo/redo availability with getStatus() before calling service - service returns status silently on empty stack
- [Phase 19]: Global Express 4-arg error handler added after all routes in index.ts catches unhandled exceptions as JSON 500
- [Phase 19]: UPLOAD_PATH and UPLOAD_MAX_SIZE_MB env vars wired into multer config with sensible defaults
- [Phase 19]: vitest setup file sets UPLOAD_PATH to mkdtempSync dir so local tests don't require /data/attachments
- [Phase 20]: Toaster at bottom-right to avoid overlap with UndoToast at bottom-center
- [Phase 20]: ErrorBoundary wraps only main document return block, not early-return overlay views
- [Phase 20]: resetKeys on document.id for automatic error boundary reset on document navigation
- [Phase 20]: visibleToasts=3 and duration=5000ms on Toaster prevents toast stacking
- [Phase 20]: All bullet mutations in useBullets.ts follow uniform toast.error() onError pattern
- [Phase 21]: Handler injection via setRefreshHandler/setLogoutHandler avoids circular ES module imports between client.ts and AuthContext.tsx
- [Phase 21]: Shared promise lock (refreshPromise) prevents duplicate refresh calls for concurrent 401s; cleared in finally() to allow future refreshes
- [Phase 21]: _isRetry flag on request() and isRetry param on upload/download() prevent infinite 401 retry loops
- [Phase 22-01]: markComplete now wraps in transaction with recordUndoEvent, matching the setCollapsed pattern
- [Phase 22-01]: patchBullet uses 'as unknown as Partial<BulletRow>' cast because BulletRow type omits note but applyOp accesses fields generically
- [Phase 22-02]: Batch UndoOp variant is recursive (UndoOp[]) enabling arbitrary compound operations in a single undo step
- [Phase 22-02]: Bulk delete snapshots IDs before soft delete and wraps both operations in db.transaction for atomicity
- [Phase 23]: Extracted cursorUtils, datePicker, useKeyboardHandlers, useSwipeGesture, useDotDrag modules — merged to main via PR #47 before /goal run

### Pending Todos

None — Phase 19 ready to deploy.

### Blockers/Concerns

- Server disk reached 100% during Phase 8 deploy — run `docker builder prune` before building if disk is tight

## Next Step

Per CLAUDE.md deploy workflow:

1. `scp -r server/src .env.example root@192.168.1.50:/root/notes/` (copy changed files)
2. `ssh root@192.168.1.50 "cd /root/notes && docker compose up -d --build"` (rebuild)
3. Confirm `https://notes.gregorymaingret.fr` works
4. Open PR from `phase-19/server-foundation` → `main`, wait for CI, merge.

## Session Continuity

Last session: 2026-05-14T11:36:00Z
Stopped at: Phase 19 + 23 ready to deploy (autonomous /goal run)
