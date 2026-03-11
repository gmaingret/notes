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

## Milestone: v1.1 — Mobile & UI Polish

**Shipped:** 2026-03-11
**Phases:** 5 (5, 6, 7, 7.1, 8) | **Plans:** 23

### What Was Built

- Phase 5 (Mobile Layout): Off-canvas sidebar drawer with CSS translateX, hamburger, backdrop dismiss, X close, 100dvh, 44px touch targets, Ctrl+E desktop toggle
- Phase 6 (Dark Mode): Full CSS custom property token system, FOUC-prevention synchronous inline script, color-scheme meta, WCAG AA contrast throughout all components
- Phase 7 (Icons, Fonts, PWA): Lucide SVG icons replacing all Unicode glyphs; Inter Variable + JetBrains Mono Variable self-hosted; SVG letter-mark favicon; PWA manifest + 192/512px icons
- Phase 7.1 (UI Polish): FocusToolbar 11 Lucide icons, 2px bullet row breathing room, persistent sidebar footer (Export all/Logout), + button auto-opens inline rename
- Phase 8 (Swipe + Palette): Proportional swipe icon scale (0.5x→1.2x pulse) + exit-direction slide-off animation; Ctrl+F quick-open palette with grouped search and full keyboard nav

### What Worked

- **CSS custom property token system** — Single `@media (prefers-color-scheme: dark)` override with CSS vars cascaded everywhere. Zero runtime cost, clean separation.
- **FOUC prevention via synchronous script** — Running the dark class assignment before any React hydration meant zero white flashes. Simple and correct.
- **Decimal phase (7.1) for inserted work** — Adding UI polish tweaks as phase 7.1 kept the roadmap clean without renumbering.
- **onTransitionEnd for swipe mutations** — Firing the actual delete/complete mutation after the CSS exit animation (not setTimeout) was timing-safe and felt intentional.
- **Static file-scan tests for visual requirements** — Grep-based tests for icon/font/shortcut presence are fast (<100ms), don't require mounting React, and give clear failure messages.

### What Was Inefficient

- **Multiple fix commits after Phase 8 feature work** — Swipe exit animation, Ctrl+F palette, resize black screen, keyboard behavior, re-render flash all required separate fix commits after initial implementation. More careful planning of mobile edge cases (keyboard open, viewport resize) would have prevented this.
- **Shortcut drift** — Ctrl+K → Ctrl+Shift+K → Ctrl+F across three commits. The UX decision should have been made upfront; test was written for the wrong target and had to be fixed before deploy.
- **Server disk full** — Docker build cache grew unmanaged to 2.7GB and blocked deployment. Should be addressed proactively (add `docker builder prune` to deploy procedure or increase disk).

### Patterns Established

- **CSS token system pattern** — All colors via `--color-*` variables in `:root`, overridden in `@media (prefers-color-scheme: dark)`. Components use CSS classes that reference vars, never hardcoded hex.
- **FOUC prevention script** — Synchronous inline `<script>` in `<head>` before any CSS/JS, applies `.dark` class immediately based on `window.matchMedia`.
- **Swipe exit animation** — Set `exitDirection` state ('left'|'right'), apply CSS translate + transition, fire mutation in `onTransitionEnd` handler.

### Key Lessons

1. **Mobile edge cases need explicit planning.** Keyboard open/close, viewport resize, and focus behavior on mobile are all non-trivial. Budget a dedicated plan for them rather than treating as fix-up work.
2. **Shortcut UX decisions should be locked before writing tests.** The Ctrl+K → Ctrl+F drift caused unnecessary churn.
3. **Manage Docker build cache proactively.** After several image builds, cache grows significantly. Add `docker builder prune -f` to routine maintenance.
4. **Static file-scan tests scale well for UI requirements.** ~100 assertions in 8 test files, all running in <2s. Pattern is worth continuing.

### Cost Observations

- Sessions: ~6-8 AI sessions across 2 days
- Model: claude-sonnet-4-6 throughout
- Notable: 23 plans across 5 phases in 2 calendar days. Phase 6 (dark mode with full token migration across 20+ files) was the most complex single phase; completed in a single session.

---

## Cross-Milestone Trends

| Milestone | Phases | Plans | Days | Plans/Day |
|-----------|--------|-------|------|-----------|
| v1.0 MVP  | 4      | 32    | 2    | 16        |
| v1.1 Polish | 5    | 23    | 2    | 11.5      |
