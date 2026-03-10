# Retrospective: Notes

## Milestone: v1.0 — MVP

**Shipped:** 2026-03-10
**Phases:** 4 | **Plans:** 32

### What Was Built

- Phase 1 (Foundation): Authenticated self-hosted web app with Docker deployment — email/password + Google SSO, document management (CRUD, reorder, export), and production deployment at notes.gregorymaingret.fr
- Phase 2 (Core Outliner): Full infinite bullet outliner — CRUD, nesting, keyboard shortcuts (Tab/Shift+Tab/Ctrl+arrows/Ctrl+Z/Y), drag-and-drop reorder, collapse/zoom with breadcrumbs, server-persisted undo/redo (50 levels, refresh-safe)
- Phase 3 (Rich Content): Inline markdown rendering with Dynalist-style live toggle, #tag/@mention/!!date chip syntax with date picker, Tag Browser sidebar, full-text search, bullet bookmarks
- Phase 4 (Attachments, Comments, Mobile): File attachments (image Lightbox, PDF thumbnail, any file type), bullet comments with slide-in panel, mobile swipe gestures (right=complete, left=delete), long-press context menu, FocusToolbar above virtual keyboard

### What Worked

- **TDD via Nyquist Wave 0** — Writing RED scaffold tests first made implementation requirements concrete before any code was written. Prevented scope drift.
- **Phase-by-phase deployment** — Each phase ended with a live production deploy and human verification. No big-bang integration surprises.
- **Deploy-before-merge workflow** — Pushing the branch, deploying it, verifying live, then merging caught environment-specific bugs (Docker tsc strictness, migration journal gaps) before they hit main.
- **FLOAT8 midpoint positioning locked at schema creation** — Prevented a breaking migration in Phase 2 when bullet reordering was implemented.
- **Plain contenteditable (not ProseMirror)** — The right call for a tree structure. Would have been much harder to extract from a document model.
- **Server-side undo from Phase 2** — Building the undo system early, before complex features layered on, meant it wrapped everything naturally.

### What Was Inefficient

- **Drizzle migration journal gap** — `_journal.json` had to be manually fixed in Phase 4 when `migrate()` silently skipped the 0002 migration. Should have been caught earlier.
- **Phase 2 SUMMARY frontmatter** — `requirements_completed` fields left empty in 02-01 through 02-08. Documentation debt only, but added noise to the audit.
- **04-09 gap closure** — Phase 4's `attach-file` and `focus-note` CustomEvent listeners weren't wired in Phase 4 main execution; required a separate gap closure plan (04-09) and branch after the audit. The event-wiring pattern should be validated at plan completion time.
- **Audit file predates 04-09** — Phase 4 VERIFICATION.md was written before the gap closure, so it doesn't reflect the fixed state of ATT-02/CMT-02/CMT-03.

### Patterns Established

- **Wave 0 RED scaffolds** — Always write failing test stubs for all phase requirements before any implementation.
- **Deploy plan as final plan in each phase** — Dedicated "deploy + verify" plan (01-06, 02-08, 03-09, 04-08) ensures production state matches expectation before moving on.
- **Logic-level unit tests for keyboard shortcuts** — Validate guard conditions and path constants without mounting React hooks; faster and less brittle.
- **Drizzle journal must be complete** — Every migration SQL file needs a matching journal entry or `migrate()` silently skips it.
- **Docker tsc -b is stricter than tsc --noEmit** — Use wrapper functions instead of casts when TypeScript composite build rejects overlapping type assertions.
- **Portal ContextMenu and Lightbox to `<body>`** — Components inside CSS transforms must be portaled out to render correctly in viewport.

### Key Lessons

1. **Lock schema decisions before any data exists.** FLOAT8 positions, soft-delete column, undo tables — all must be in migration 0 or you pay a breaking migration later.
2. **Drizzle `_journal.json` is not auto-updated.** Any manually added migration SQL must also be added to the journal, or `migrate()` skips it silently.
3. **FocusToolbar belongs in BulletTree, not DocumentView.** DndContext in BulletTree captures pointer events — toolbar must live inside that same context.
4. **Gap closure should be part of phase verification, not a separate plan.** Event wiring and feature integration gaps surfaced by the audit should be caught in VERIFICATION.md before moving to the next phase.
5. **Deploy-before-merge is non-negotiable.** Docker builds are stricter than local TypeScript checks — always verify on the actual Docker image before merging.

### Cost Observations

- Sessions: ~8-10 AI sessions across 2 days
- Model: claude-sonnet-4-6 throughout
- Notable: All 32 plans executed in ~2 calendar days with no major blockers. Velocity was consistently fast (avg ~20 min/plan for implementation plans, ~5 min for deploy/verify plans).

---

## Cross-Milestone Trends

| Milestone | Phases | Plans | Days | Plans/Day |
|-----------|--------|-------|------|-----------|
| v1.0 MVP  | 4      | 32    | 2    | 16        |
