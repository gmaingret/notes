# Roadmap: Notes

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-10)
- ✅ **v1.1 Mobile & UI Polish** — Phases 5-8 (shipped 2026-03-11)
- 🚧 **v2.0 Native Android Client** — Phases 9-12 (in progress)

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

### 🚧 v2.0 Native Android Client (In Progress)

**Milestone Goal:** Native Android client in Kotlin/Jetpack Compose + Material Design 3 that talks to the existing backend API — no new backend features, focus on reactivity and polish.

- [x] **Phase 9: Android Foundation and Auth** — Project scaffold with Clean Architecture, Hilt DI, OkHttp auth infrastructure, login and register screens (completed 2026-03-12)
- [ ] **Phase 10: Document Management** — ModalNavigationDrawer with full document CRUD, drag reorder, last-opened persistence
- [ ] **Phase 11: Bullet Tree** — Flat LazyColumn outliner with all core editing interactions, markdown rendering, and chip syntax
- [ ] **Phase 12: Reactivity and Polish** — Swipe gestures, search, undo/redo, pull-to-refresh, animations, attachments, bookmarks

## Phase Details

### Phase 9: Android Foundation and Auth
**Goal**: Users can register, log in, and silently re-authenticate on the production server from a native Android app
**Depends on**: Nothing (first phase of v2.0)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06
**Success Criteria** (what must be TRUE):
  1. User can register a new account with email and password from the Android app
  2. User can log in with email and password and reach the main screen
  3. User can log in with Google SSO via the Credential Manager picker
  4. On cold start after a previous login, the app opens directly to the main screen without prompting for credentials
  5. All API requests include a valid Bearer token; a 401 response triggers exactly one token refresh (no concurrent refresh races)
**Plans**: 5 plans
Plans:
- [ ] 09-01-PLAN.md — Android project scaffold, Gradle, version catalog, theme, domain contracts
- [ ] 09-02-PLAN.md — Backend POST /auth/google/token endpoint
- [ ] 09-03-PLAN.md — Auth data layer: token store, cookie jar, interceptor, authenticator, DI
- [ ] 09-04-PLAN.md — Auth UI: login/register screen, main screen, navigation, splash
- [ ] 09-05-PLAN.md — Google SSO (Credential Manager), tests, CI

### Phase 10: Document Management
**Goal**: Users can manage their document list in a native Android drawer and navigate between documents
**Depends on**: Phase 9
**Requirements**: DOCM-01, DOCM-02, DOCM-03, DOCM-04, DOCM-05, DOCM-06
**Success Criteria** (what must be TRUE):
  1. User can open the drawer and see all their documents listed
  2. User can create, rename, and delete documents from the drawer
  3. User can drag documents in the drawer to reorder them
  4. Tapping a document opens its content in the main area
  5. On cold start, the app re-opens the last document the user was viewing
**Plans**: 3 plans
Plans:
- [ ] 10-01-PLAN.md — Domain model, API, DTOs, repository, DI, lastDocId persistence
- [ ] 10-02-PLAN.md — Use cases, MainViewModel with document state, unit tests
- [ ] 10-03-PLAN.md — Drawer UI, document rows, drag-reorder, MainScreen wiring, checkpoint

### Phase 11: Bullet Tree
**Goal**: Users can create, edit, and organize an infinitely nested bullet outline with full keyboard loop and rich text rendering
**Depends on**: Phase 10
**Requirements**: TREE-01, TREE-02, TREE-03, TREE-04, TREE-05, TREE-06, TREE-07, TREE-08, TREE-09, TREE-10, TREE-11, CONT-01, CONT-02
**Success Criteria** (what must be TRUE):
  1. User can tap a bullet to edit it inline; pressing Enter creates a new sibling below; pressing Enter again on the empty bullet outdents it instead of creating another
  2. User can indent and outdent bullets via toolbar buttons; children follow their parent
  3. User can collapse a bullet with children and its subtree disappears; tapping again re-expands it
  4. User can tap any bullet's icon to zoom into it as the root, with a breadcrumb trail showing the path back
  5. Bullet text displays markdown formatting (bold, italic, strikethrough, links) and #tags/@mentions/!!dates appear as tappable chips
  6. User can view and add comments on a bullet
**Plans**: TBD

### Phase 12: Reactivity and Polish
**Goal**: The app feels fast and native — gestures work, search finds content, undo works, and all screens handle loading and error states correctly
**Depends on**: Phase 11
**Requirements**: CONT-03, CONT-04, POLL-01, POLL-02, POLL-03, POLL-04, POLL-05, POLL-06, POLL-07, POLL-08
**Success Criteria** (what must be TRUE):
  1. Swiping right on a bullet marks it complete; swiping left deletes it, both with proportional color and icon reveal
  2. Searching from the top bar returns matching documents and bullets with 300ms debounce
  3. Tapping undo in the toolbar reverts the last mutation (up to 50 levels)
  4. All screens show a loading state on first load, an error state with retry on failure, and pull-to-refresh restores current data
  5. User can view a dedicated bookmarks screen listing all bookmarked bullets
**Plans**: TBD

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
| 9. Android Foundation and Auth | 5/5 | Complete   | 2026-03-12 | - |
| 10. Document Management | v2.0 | 0/TBD | Not started | - |
| 11. Bullet Tree | v2.0 | 0/TBD | Not started | - |
| 12. Reactivity and Polish | v2.0 | 0/TBD | Not started | - |
