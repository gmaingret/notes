# Roadmap: Notes

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-10)
- ✅ **v1.1 Mobile & UI Polish** — Phases 5-8 (shipped 2026-03-11)
- ✅ **v2.0 Native Android Client** — Phases 9-12 (shipped 2026-03-14)
- 🔄 **v2.1 Android Home Screen Widget** — Phases 13-15 (in progress)

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

### v2.1 Android Home Screen Widget

- [x] **Phase 13: Widget Foundation** — Placeable widget with document picker, correct Glance/Hilt wiring, and full display state rendering (completed 2026-03-14)
- [x] **Phase 14: Background Sync and Auth** — WorkManager periodic refresh, independent widget auth via refresh cookie, and in-app broadcast update (completed 2026-03-14)
- [ ] **Phase 15: Interactive Actions** — Delete bullet from widget and add bullet via overlay Activity

## Phase Details

### Phase 13: Widget Foundation
**Goal**: Users can place a Notes widget on their home screen, pick a document, and see its root bullets rendered with all display states correct
**Depends on**: Phase 12 (existing Android app with Clean Architecture, Hilt, DataStore+Tink, Retrofit)
**Requirements**: SETUP-01, SETUP-02, DISP-01, DISP-02, DISP-03, DISP-04, DISP-05
**Success Criteria** (what must be TRUE):
  1. User can long-press home screen, select the Notes widget, and place it without the launcher silently discarding it
  2. A document picker appears immediately on widget placement and the widget shows that document's content after the user confirms
  3. The widget header shows the selected document's title
  4. The widget body shows root-level bullets as a scrollable flat list, and shows a clear empty state when the document has no bullets
  5. The widget shows distinct loading and error states with readable messages; the widget visual style matches the app's Material 3 theme
**Plans**: 4 plans
Plans:
- [x] 13-01-PLAN.md — Glance infrastructure, type contracts, WidgetStateStore, manifest registration
- [x] 13-02-PLAN.md — Document picker config activity with auth gate
- [x] 13-03-PLAN.md — Widget UI composables and all display states
- [x] 13-04-PLAN.md — Gap closure: wire documentId through widget tap intents and add onDeleted test

### Phase 14: Background Sync and Auth
**Goal**: The widget stays current across device sessions, app restarts, and process death without requiring the user to manually intervene
**Depends on**: Phase 13
**Requirements**: SYNC-01, SYNC-02, SYNC-03
**Success Criteria** (what must be TRUE):
  1. After making bullet changes in the Android app, the home screen widget reflects those changes without any manual action by the user
  2. With the Android app force-stopped overnight, the widget still shows current content the next morning (WorkManager 15-minute periodic sync ran)
  3. After a device reboot where the app has never been opened, the widget successfully authenticates and loads content using the persisted refresh token — no "sign in" prompt appears unless the session has genuinely expired
**Plans**: 2 plans
Plans:
- [ ] 14-01-PLAN.md — WorkManager infrastructure, WidgetStateStore cache extensions, WidgetSyncWorker
- [ ] 14-02-PLAN.md — provideGlance cache refactor, enqueue/cancel lifecycle, in-app mutation triggers

### Phase 15: Interactive Actions
**Goal**: Users can add and delete bullets directly from the home screen widget without opening the app
**Depends on**: Phase 14
**Requirements**: ACT-01, ACT-02
**Success Criteria** (what must be TRUE):
  1. Tapping the "+" button in the widget opens a lightweight overlay dialog with a pre-focused text field; the new bullet is created at the top of the list and appears in the widget immediately after confirmation
  2. Tapping the delete icon on any widget bullet row removes that bullet; the widget updates to reflect the deletion without the user needing to open the app or refresh manually
**Plans**: 2 plans
Plans:
- [ ] 15-01-PLAN.md — Delete bullet ActionCallback with optimistic remove, rollback, and x icon on bullet rows
- [ ] 15-02-PLAN.md — Add bullet overlay Activity with text field dialog, optimistic insert, and [+] header wiring

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
| 14. Background Sync and Auth | 2/2 | Complete    | 2026-03-14 | - |
| 15. Interactive Actions | v2.1 | 0/2 | Not started | - |
