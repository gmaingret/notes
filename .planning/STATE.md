---
gsd_state_version: 1.0
milestone: v2.1
milestone_name: Android Home Screen Widget
status: roadmap_ready
stopped_at: Completed 15-02-PLAN.md
last_updated: "2026-03-14T15:43:48.960Z"
last_activity: 2026-03-14 — Roadmap created for v2.1 (Phases 13-15)
progress:
  total_phases: 3
  completed_phases: 3
  total_plans: 8
  completed_plans: 8
---

---
gsd_state_version: 1.0
milestone: v2.1
milestone_name: Android Home Screen Widget
status: roadmap_ready
stopped_at: null
last_updated: "2026-03-14T00:00:00Z"
last_activity: 2026-03-14 — Roadmap created for v2.1 (Phases 13-15)
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-14)

**Core value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.
**Current focus:** v2.1 Android Home Screen Widget — ready to plan Phase 13

## Current Position

Phase: 13 — Widget Foundation (not started)
Plan: —
Status: Roadmap approved, ready for planning
Last activity: 2026-03-14 — Roadmap created for v2.1 (Phases 13-15)

Progress: [░░░░░░░░░░] 0% (v2.1: 0/3 phases)

## Performance Metrics

**Velocity (v2.0 reference):**
- v2.0: 17 plans across 4 phases, ~12,200 LOC Kotlin

**By Phase (v2.1):**

| Phase | Plans | Status |
|-------|-------|--------|
| 13. Widget Foundation | TBD | Not started |
| 14. Background Sync and Auth | TBD | Not started |
| 15. Interactive Actions | TBD | Not started |

*Updated after each plan completion*
| Phase 13-widget-foundation P01 | 10 | 3 tasks | 16 files |
| Phase 13-widget-foundation P02 | 8 | 2 tasks | 4 files |
| Phase 13-widget-foundation P03 | 30 | 2 tasks | 5 files |
| Phase 13-widget-foundation PP04 | 20 | 2 tasks | 7 files |
| Phase 14-background-sync-and-auth P01 | 14 | 2 tasks | 8 files |
| Phase 14-background-sync-and-auth P02 | 525623 | 2 tasks | 8 files |
| Phase 15-interactive-actions P01 | 9 | 2 tasks | 4 files |
| Phase 15-interactive-actions P02 | 20 | 2 tasks | 6 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Decisions affecting v2.1 (from research):

- v2.1 stack: Jetpack Glance 1.1.1 + Glance Material3 1.1.1 (stable; 1.1.1 includes CVE-2024-7254 fix)
- v2.1 stack: WorkManager KTX 2.11.1 + Hilt Work 1.3.0 with ksp (not kapt)
- v2.1 arch: Hilt cannot @AndroidEntryPoint into GlanceAppWidget — use @EntryPoint + EntryPointAccessors.fromApplication() inside provideGlance()
- v2.1 arch: WidgetStateStore is a custom DataStore singleton (not Glance PreferencesGlanceStateDefinition) — accessible from WidgetConfigActivity before first render and from NotesWidgetReceiver.onDeleted()
- v2.1 arch: updatePeriodMillis="0" in appwidget-provider XML — WorkManager exclusively owns the schedule (OEM battery management suppresses broadcast-based polling)
- v2.1 arch: Use updateAll() everywhere except inside ActionCallback.onAction() — never hard-code a single glanceId
- v2.1 arch: All I/O inside provideGlance() via withContext(Dispatchers.IO) — never inside provideContent{} lambda
- v2.1 arch: Write to DataStore first, then call update() — provideGlance() re-reads DataStore on every recompose
- v2.1 auth: In-memory access token is null in widget process — widget must read persisted refresh cookie from Tink-encrypted DataStore and call /auth/refresh independently (Phase 14 design decision)
- v2.1 arch: NotesApplication must implement Configuration.Provider + inject HiltWorkerFactory; disable default WorkManager auto-initializer in AndroidManifest.xml via tools:node="remove"
- v2.1 arch: SizeMode.Responsive with 2-3 predefined DpSize breakpoints (not SizeMode.Exact)
- v2.1 arch: WidgetConfigActivity must set RESULT_CANCELED at start; switch to RESULT_OK only on user confirmation — omitting this causes launcher to silently discard widget placement
- [Phase 13-widget-foundation]: WidgetStateStore createForTest() factory enables Robolectric testing with mock Aead — avoids Android Keystore dependency in JVM tests
- [Phase 13-widget-foundation]: fetchWidgetData accepts WidgetEntryPoint parameter (not Context) — enables pure Kotlin unit testing without Robolectric for all business logic
- [Phase 13-widget-foundation]: EncryptedDataStoreFactory.getWidgetStateAead() uses isolated widget_state_keyset separate from auth/cookie keysets for domain separation
- [Phase 13-widget-foundation]: WidgetModule provides WidgetStateStore via @Provides since manual singleton pattern requires explicit Hilt binding
- [Phase 13-widget-foundation]: Google SSO in config activity: GoogleSignInUseCase injected into Activity (needs Activity context); ViewModel only receives idToken via loginWithGoogle(idToken)
- [Phase 13-widget-foundation]: documentSelectedEvent uses Channel<Unit> (not SharedFlow) for one-shot delivery semantics preventing event replay on Activity recreation
- [Phase 13-widget-foundation]: Glance 1.1.1 ColorProviders lacks outlineVariant — use outline for dividers; actionRunCallback is in androidx.glance.appwidget.action not glance.action; !!date regex uses \S+ not digit-only
- [Phase 13-widget-foundation]: consumeWidgetDocumentId() pattern chosen over StateFlow: returns-and-clears atomically, no new state propagation needed
- [Phase 13-widget-foundation]: Robolectric required for NotesWidgetReceiverTest.onDeleted: GlanceAppWidgetReceiver.onDeleted calls goAsync() which requires Android broadcast machinery
- [Phase 14-background-sync-and-auth]: WidgetSyncWorker always returns Result.success() to keep periodic schedule alive — errors communicated via WidgetStateStore display state instead
- [Phase 14-background-sync-and-auth]: Non-auth network errors in WidgetSyncWorker keep stale cache unchanged — stale data preferred over blank widget
- [Phase 14-background-sync-and-auth]: getFirstDocumentId() iterates DataStore keys matching widget_doc_* prefix to find configured widget without needing appWidgetId in worker context
- [Phase 14-background-sync-and-auth]: triggerWidgetRefreshIfNeeded() logic extracted to WidgetRefreshHelper.kt as internal suspend fun for unit testability
- [Phase 14-background-sync-and-auth]: NotesWidget.provideGlance reads exclusively from WidgetStateStore cache — no live API calls in widget renderer
- [Phase 14-background-sync-and-auth]: MainViewModel injects @ApplicationContext + WidgetStateStore to write SESSION_EXPIRED on logout without AndroidViewModel
- [Phase 15-interactive-actions]: performDelete() extracted as internal suspend fun for pure JVM unit testability without Robolectric
- [Phase 15-interactive-actions]: Two-layer clickable in BulletRow: inner Row (dot+text) with actionStartActivity, x Box with actionRunCallback — innermost clickable wins in Glance
- [Phase 15-interactive-actions]: AddBulletActivity uses @AndroidEntryPoint (not @EntryPoint) — Activity context works with Hilt direct injection
- [Phase 15-interactive-actions]: performAddBullet returns sealed AddBulletResult instead of nullable String for exhaustive when handling
- [Phase 15-interactive-actions]: [+] button only appears in ContentView HeaderRow — non-Content states have no doc_id to pass

### Research Flags

- **Phase 14 (auth strategy):** Validate whether the shared OkHttpClient with AuthInterceptor handles worker context correctly, or whether a worker-scoped client is needed. Confirm /auth/refresh behavior when called from CoroutineWorker with DataStore-read cookie.
- **Phase 13 (LazyColumn item count):** Test with 30+ root-level bullets — Glance LazyColumn backed by ListView has a soft cap on RemoteViews children count.

### Pending Todos

None.

### Blockers/Concerns

None at roadmap stage. Phase 14 auth strategy flagged for design review during planning (see Research Flags above).

## Session Continuity

Last session: 2026-03-14T15:40:13.929Z
Stopped at: Completed 15-02-PLAN.md
Resume file: None
