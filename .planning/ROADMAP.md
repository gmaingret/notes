# Roadmap: Notes

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-10)
- ✅ **v1.1 Mobile & UI Polish** — Phases 5-8 (shipped 2026-03-11)
- ✅ **v2.0 Native Android Client** — Phases 9-12 (shipped 2026-03-14)
- ✅ **v2.1 Android Home Screen Widget** — Phases 13-15 (shipped 2026-03-15)
- ✅ **v2.2 Security Hardening** — Phases 16-18 (shipped 2026-03-15)
- 🚧 **v2.3 Robustness & Quality** — Phases 19-23 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-4) — SHIPPED 2026-03-10</summary>

- [x] Phase 1: Foundation (6/6 plans) — completed 2026-03-09
- [x] Phase 2: Core Outliner (8/8 plans) — completed 2026-03-09
- [x] Phase 3: Rich Content (9/9 plans) — completed 2026-03-09
- [x] Phase 4: Attachments, Comments, and Mobile (9/9 plans) — completed 2026-03-10

Full details: [`.planning/milestones/v1.0-ROADMAP.md`](milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Mobile & UI Polish (Phases 5-8) — SHIPPED 2026-03-11</summary>

- [x] Phase 5: Mobile Layout Foundation (4/4 plans) — completed 2026-03-10
- [x] Phase 6: Dark Mode (5/5 plans) — completed 2026-03-10
- [x] Phase 7: Icons, Fonts, and PWA (5/5 plans) — completed 2026-03-10
- [x] Phase 7.1: UI Polish Tweaks (4/4 plans) — completed 2026-03-10
- [x] Phase 8: Swipe Polish and Quick-Open Palette (5/5 plans) — completed 2026-03-11

Full details: [`.planning/milestones/v1.1-ROADMAP.md`](milestones/v1.1-ROADMAP.md)

</details>

<details>
<summary>✅ v2.0 Native Android Client (Phases 9-12) — SHIPPED 2026-03-14</summary>

- [x] Phase 9: Android Foundation and Auth (5/5 plans) — completed 2026-03-12
- [x] Phase 10: Document Management (3/3 plans) — completed 2026-03-12
- [x] Phase 11: Bullet Tree (4/4 plans) — completed 2026-03-12
- [x] Phase 12: Reactivity and Polish (5/5 plans) — completed 2026-03-12

Full details: [`.planning/milestones/v2.0-ROADMAP.md`](milestones/v2.0-ROADMAP.md)

</details>

<details>
<summary>✅ v2.1 Android Home Screen Widget (Phases 13-15) — SHIPPED 2026-03-15</summary>

- [x] Phase 13: Widget Foundation (4/4 plans) — completed 2026-03-14
- [x] Phase 14: Background Sync and Auth (2/2 plans) — completed 2026-03-14
- [x] Phase 15: Interactive Actions (2/2 plans) — completed 2026-03-14

Full details: [`.planning/milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md)

</details>

<details>
<summary>✅ v2.2 Security Hardening (Phases 16-18) — SHIPPED 2026-03-15</summary>

- [x] Phase 16: Injection and Upload Hardening (2/2 plans) — completed 2026-03-15
- [x] Phase 17: Auth and Session Security (2/2 plans) — completed 2026-03-15
- [x] Phase 18: API Protection (1/1 plans) — completed 2026-03-15

Full details: [`.planning/milestones/v2.2-ROADMAP.md`](milestones/v2.2-ROADMAP.md)

</details>

### 🚧 v2.3 Robustness & Quality (In Progress)

**Milestone Goal:** Improve reliability, error handling, developer experience, and code quality across the full stack — CI/CD pipelines, automatic token refresh, error boundaries, toast notifications, undo coverage extension, and component refactoring.

- [ ] **Phase 19: Server Foundation** — Standardize API error format, fix undo route error handling, add CI/CD workflows, wire upload env vars
- [x] **Phase 20: Client Infrastructure** — Add React error boundary at document level and global toast notifications for mutation failures (completed 2026-03-19)
- [x] **Phase 21: Token Refresh Interceptor** — Automatic silent token refresh on 401 with race condition prevention and retry guard (completed 2026-03-19)
- [ ] **Phase 22: Undo Coverage Extension** — Extend undo/redo to cover mark-complete, note edits, and bulk delete of completed bullets
- [ ] **Phase 23: Component Refactoring** — Decompose BulletContent and BulletNode into focused, testable sub-components

## Phase Details

### Phase 19: Server Foundation
**Goal**: Server consistently returns structured errors, CI validates PRs automatically, and upload config is controlled by environment variables
**Depends on**: Phase 18 (last completed phase)
**Requirements**: ERR-01, ERR-02, CICD-01, CICD-02, CONF-01
**Success Criteria** (what must be TRUE):
  1. Every API endpoint returns errors as `{ error: string }` JSON — no raw HTML, no mixed `{ errors }` format
  2. Calling undo when there is nothing to undo returns a 422 with a human-readable message instead of a 500
  3. A PR to main triggers a GitHub Actions workflow that runs server lint and tests and fails the PR if they fail
  4. A PR to main triggers a GitHub Actions workflow that runs client lint and Vite build validation and fails the PR if they fail
  5. Changing UPLOAD_MAX_SIZE_MB or UPLOAD_PATH in .env changes the actual upload behavior without a code change
**Plans**: 2 plans
Plans:
- [ ] 19-01-PLAN.md — Error handling, undo fix, upload env var wiring
- [ ] 19-02-PLAN.md — CI/CD workflows (server-ci.yml, client-ci.yml)

### Phase 20: Client Infrastructure
**Goal**: The web client has a global error boundary that prevents full-screen crashes and a toast notification layer that surfaces mutation failures to the user
**Depends on**: Phase 19
**Requirements**: ERR-03, RES-02
**Success Criteria** (what must be TRUE):
  1. A rendering crash inside a document shows an error card for that document instead of blanking the whole application
  2. Navigating from a crashed document to a healthy one clears the error card and renders the new document normally
  3. When a bullet save, delete, or reorder fails on the server, a toast notification appears describing the failure
  4. Toast notifications disappear automatically and do not stack into an unreadable pile
**Plans**: 2 plans
Plans:
- [x] 20-01-PLAN.md — Error boundary at DocumentView level with auto-reset
- [x] 20-02-PLAN.md — Toast notifications for mutation failures via sonner

### Phase 21: Token Refresh Interceptor
**Goal**: Expired access tokens are refreshed silently in the background so the user never sees a failed mutation due to token expiry
**Depends on**: Phase 20
**Requirements**: RES-01
**Success Criteria** (what must be TRUE):
  1. After an access token expires, the next mutation (create bullet, edit, delete) succeeds without any visible interruption
  2. If multiple requests are in-flight when the token expires, only one refresh call is made to the server (not one per request)
  3. If the refresh token is also expired, the user is logged out and redirected to the login page with a notification
  4. A request that has already been retried once does not trigger another refresh — it fails cleanly instead of looping
**Plans**: 1 plan
Plans:
- [ ] 21-01-PLAN.md — 401 interceptor with shared promise lock, retry guard, and AuthContext handler injection

### Phase 22: Undo Coverage Extension
**Goal**: Users can undo and redo marking bullets complete, editing notes, and bulk-deleting completed bullets — consistent with the existing 50-level global undo promise
**Depends on**: Phase 19, Phase 20
**Requirements**: UNDO-01, UNDO-02, UNDO-03
**Success Criteria** (what must be TRUE):
  1. Marking a bullet complete and pressing Ctrl+Z restores it to incomplete
  2. Editing a bullet's note text and pressing Ctrl+Z restores the previous note content
  3. Bulk-deleting all completed bullets and pressing Ctrl+Z restores all of them in a single undo step
  4. Redo works correctly after each of the above undos (Ctrl+Y re-applies the action)
**Plans**: 2 plans
Plans:
- [ ] 22-01-PLAN.md — Undo for mark-complete toggle and note edits (UNDO-01, UNDO-02)
- [ ] 22-02-PLAN.md — Batch op type and undo for bulk delete completed (UNDO-03)

### Phase 23: Component Refactoring
**Goal**: BulletContent and BulletNode are decomposed into focused sub-components that can be read, tested, and modified independently
**Depends on**: Phase 22
**Requirements**: QUAL-01, QUAL-02
**Success Criteria** (what must be TRUE):
  1. BulletContent no longer exceeds 300 lines — cursor helpers and keyboard logic live in separate, importable modules
  2. BulletNode no longer exceeds 250 lines — swipe gesture logic is extracted into a reusable hook
  3. All existing bullet interactions (edit, indent, drag, swipe, complete, zoom) continue to work identically after refactoring
  4. At least one extracted module (e.g., cursorUtils) has standalone unit tests that pass without mounting a React component
**Plans**: 2 plans
Plans:
- [ ] 23-01-PLAN.md — [To be planned]
- [ ] 23-02-PLAN.md — [To be planned]

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation | v1.0 | 6/6 | Complete | 2026-03-09 |
| 2. Core Outliner | v1.0 | 8/8 | Complete | 2026-03-09 |
| 3. Rich Content | v1.0 | 9/9 | Complete | 2026-03-09 |
| 4. Attachments, Comments, and Mobile | v1.0 | 9/9 | Complete | 2026-03-10 |
| 5. Mobile Layout Foundation | v1.1 | 4/4 | Complete | 2026-03-10 |
| 6. Dark Mode | v1.1 | 5/5 | Complete | 2026-03-10 |
| 7. Icons, Fonts, and PWA | v1.1 | 5/5 | Complete | 2026-03-10 |
| 7.1. UI Polish Tweaks | v1.1 | 4/4 | Complete | 2026-03-10 |
| 8. Swipe Polish and Quick-Open Palette | v1.1 | 5/5 | Complete | 2026-03-11 |
| 9. Android Foundation and Auth | v2.0 | 5/5 | Complete | 2026-03-12 |
| 10. Document Management | v2.0 | 3/3 | Complete | 2026-03-12 |
| 11. Bullet Tree | v2.0 | 4/4 | Complete | 2026-03-12 |
| 12. Reactivity and Polish | v2.0 | 5/5 | Complete | 2026-03-12 |
| 13. Widget Foundation | v2.1 | 4/4 | Complete | 2026-03-14 |
| 14. Background Sync and Auth | v2.1 | 2/2 | Complete | 2026-03-14 |
| 15. Interactive Actions | v2.1 | 2/2 | Complete | 2026-03-14 |
| 16. Injection and Upload Hardening | v2.2 | 2/2 | Complete | 2026-03-15 |
| 17. Auth and Session Security | v2.2 | 2/2 | Complete | 2026-03-15 |
| 18. API Protection | v2.2 | 1/1 | Complete | 2026-03-15 |
| 19. Server Foundation | v2.3 | 0/2 | Planning | - |
| 20. Client Infrastructure | v2.3 | 2/2 | Complete | 2026-03-19 |
| 21. Token Refresh Interceptor | 1/1 | Complete    | 2026-03-19 | - |
| 22. Undo Coverage Extension | 1/2 | In Progress|  | - |
| 23. Component Refactoring | v2.3 | 0/TBD | Not started | - |
