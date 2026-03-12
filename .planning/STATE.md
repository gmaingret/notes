---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Native Android Client
status: ready_to_plan
stopped_at: Completed 11-04-PLAN.md
last_updated: "2026-03-12T14:09:24.076Z"
last_activity: 2026-03-12 — v2.0 roadmap created, phases 9-12 defined
progress:
  total_phases: 4
  completed_phases: 3
  total_plans: 12
  completed_plans: 12
---

---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Native Android Client
status: ready_to_plan
stopped_at: "Roadmap created for v2.0 — ready to plan Phase 9"
last_updated: "2026-03-12T00:00:00Z"
last_activity: 2026-03-12 — v2.0 roadmap created, phases 9-12 defined
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-12)

**Core value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.
**Current focus:** Phase 9 — Android Foundation and Auth

## Current Position

Phase: 9 of 12 (Android Foundation and Auth)
Plan: — of TBD
Status: Ready to plan
Last activity: 2026-03-12 — v2.0 roadmap created, phases 9-12 defined

Progress: [░░░░░░░░░░] 0% (v2.0)

## Performance Metrics

**Velocity (v1.x reference):**
- v1.0: 32 plans, ~45 min avg
- v1.1: 23 plans, ~6 min avg (focused scoped plans)

**By Phase (v2.0):**

| Phase | Plans | Status |
|-------|-------|--------|
| 9. Android Foundation and Auth | TBD | Not started |
| 10. Document Management | TBD | Not started |
| 11. Bullet Tree | TBD | Not started |
| 12. Reactivity and Polish | TBD | Not started |

*Updated after each plan completion*
| Phase 09 P02 | 12 | 1 tasks | 3 files |
| Phase 09-android-foundation-and-auth P01 | 18 | 3 tasks | 28 files |
| Phase 09 P03 | 6 | 3 tasks | 13 files |
| Phase 09-android-foundation-and-auth P04 | 9 | 2 tasks | 11 files |
| Phase 09 P05 | 11 | 3 tasks | 10 files |
| Phase 10-document-management P01 | 9 | 2 tasks | 10 files |
| Phase 10 P02 | 7 | 2 tasks | 15 files |
| Phase 10 P03 | 12 | 3 tasks | 4 files |
| Phase 11-bullet-tree P01 | 14 | 2 tasks | 28 files |
| Phase 11-bullet-tree P02 | 9 | 1 tasks | 3 files |
| Phase 11-bullet-tree P03 | 13 | 2 tasks | 6 files |
| Phase 11-bullet-tree P04 | 5 | 1 tasks | 5 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting v2.0:

- v2.0 stack: DataStore 1.2.1 + Tink 1.8.0 replaces deprecated EncryptedSharedPreferences for token storage
- v2.0 stack: Navigation3 1.0.1 (not Nav2) — type-safe destinations, stable Nov 2025
- v2.0 stack: Retrofit 3.0.0 + OkHttp 4.12.0 — do NOT upgrade OkHttp to 5.x (Retrofit 3 incompatible)
- v2.0 arch: Android project lives in /android/ subdirectory — prevents Gradle from scanning node_modules
- v2.0 arch: Mutex-synchronized TokenAuthenticator is required from Phase 9 — HIGH recovery cost if retrofitted
- v2.0 arch: FlattenTreeUseCase must be a pure Kotlin recursive DFS — unit-testable without device
- v2.0 arch: BulletTreeViewModel optimistic updates designed in Phase 11 from the start — not retrofitted
- v2.0 scope: No Room database (no offline mode), no Firebase, no Play Services
- [Phase 09]: google-auth-library OAuth2Client.verifyIdToken() used for native Android Google SSO server-side verification
- [Phase 09]: POST /api/auth/google/token response shape matches email/password login exactly — Android reuses same token handler
- [Phase 09-android-foundation-and-auth]: Hilt downgraded to 2.56.1 (2.59.x requires AGP 9.0+; 2.56.1 last AGP-8.x-compatible version)
- [Phase 09-android-foundation-and-auth]: AGP bumped to 8.9.1 + compileSdk 36 (required by Navigation3 1.0.1 transitive deps)
- [Phase 09-android-foundation-and-auth]: XML theme uses android:Theme.Material.NoActionBar base; Material3 applied at runtime via NotesTheme composable (com.google.android.material not a dependency)
- [Phase 09-03]: AndroidKeysetManager used for Tink keyset management (keyset in SharedPreferences, master key in Android Keystore)
- [Phase 09-03]: DataStoreCookieJar keys cookies as 'host|name' for efficient per-host filtering on loadForRequest
- [Phase 09-03]: CheckAuthUseCase calls refresh() (not getAccessToken) to validate server-side refreshToken cookie validity on cold start
- [Phase 09-04]: AuthRoute is data class (not object) to carry showNetworkError boolean from splash flow to AuthScreen
- [Phase 09-04]: HttpException 409 field=email -> emailError; 401 -> passwordError 'Wrong email or password'; IOException -> snackbar
- [Phase 09-05]: GoogleSignInUseCase.isGoogleSignInAvailable() is companion fn so AuthScreen calls it without ViewModel holding Context
- [Phase 09-05]: testReleaseUnitTest disabled: Compose UI tests require debug test manifest (ui-test-manifest is debugImplementation-only)
- [Phase 10-01]: openDocument and deleteDocument return Response<Unit> — Gson converter throws on 204 empty body with plain Unit return type
- [Phase 10-01]: getDocuments() sorts by position ascending in the repository — avoids relying on server-side ORDER BY guarantee
- [Phase 10-01]: lastDocId stored plain (no Tink encryption) — non-sensitive UUID; clearAll() covers it via DataStore.edit { it.clear() }
- [Phase 10-01]: Reorderable 3.0.0 added in data layer plan — avoids build-config changes mid-UI development
- [Phase 10-02]: OpenDocumentUseCase.invoke() is Unit-returning — fire-and-forget; saveLastDocId always runs regardless of API result
- [Phase 10-02]: SharedFlow snackbar test: launch collect job before triggering failure, cancel after advanceUntilIdle — avoids UncompletedCoroutinesError
- [Phase 10-02]: commitReorder revert reloads full list from API rather than caching a snapshot — simpler and always server-consistent
- [Phase 10]: Delete confirmation AlertDialog rendered at MainScreen level (outside ModalDrawerSheet) — ensures correct Z-order over Scaffold content
- [Phase 10]: [Phase 10-03]: startRename(docId) added to MainViewModel — method was in Plan 02 interface spec but was missing from implementation
- [Phase 10]: [Phase 10-03]: onFocusChanged hasFocused guard prevents inline TextField cancel firing before initial focus
- [Phase 11-01]: FlattenTreeUseCase gets @Inject constructor for Hilt — javax.inject is pure Java, no Android deps, still directly instantiable in unit tests
- [Phase 11-01]: PatchBulletRequest companion factory functions enforce server single-field-per-request contract at call site
- [Phase 11-01]: BulletTreeViewModel.updateState preserves focusedBulletId across state rebuilds to prevent focus loss during silent server reloads
- [Phase 11-02]: Content/note debounce uses MutableSharedFlow(extraBufferCapacity=64) + debounce(500ms) in init{} collect — simpler than per-bullet Job cancellation, handles rapid multi-bullet edits without coroutine leaks
- [Phase 11-02]: createBullet inserts temp UUID bullet optimistically, replaced by server response on success — prevents flatList flicker while API is in flight
- [Phase 11-02]: backspaceOnEmpty reparents deleted bullet's children to deleted bullet's own parentId — keeps tree consistent without extra API call
- [Phase 11-03]: LinkAnnotation.Url + addLink require @OptIn(ExperimentalTextApi) in Compose BOM 2025.02 — applied to buildMarkdownAnnotatedString and link test method
- [Phase 11-03]: BulletRow uses FlowRow for mixed text+chip segments; pure-text bullets use buildMarkdownAnnotatedString directly to avoid FlowRow overhead
- [Phase 11-04]: Cycle prevention uses flat-list position scan (not recursive tree walk) — avoids extra method since FlattenTreeUseCase produces DFS order
- [Phase 11-04]: Note expansion state lives in BulletTreeScreen as local Set<String> — pure ephemeral UI state, no need to survive config change
- [Phase 11-04]: showSnackbar() added to BulletTreeViewModel as public method launching in viewModelScope — allows UI to trigger snackbar without coroutine context

### Pending Todos

None.

### Blockers/Concerns

- Phase 9: Refresh cookie persistence across process death — JavaNetCookieJar holds cookies in memory only; DataStore/Tink serialization wrapper must be written and validated against production server cookie format as the very first Phase 9 deliverable
- Phase 11 tech spike: ComputeDragProjectionUseCase has no Android equivalent for getBoundingClientRect — confirm Calvin-LL/Reorderable library fits flat-tree model before committing to drag architecture
- Phase 11 UX decision: Gboard Tab key interception by OEM keyboards — toolbar indent/outdent buttons must be the PRIMARY interaction path for indent/outdent; physical keyboard Tab is secondary

## Session Continuity

Last session: 2026-03-12T14:04:58.585Z
Stopped at: Completed 11-04-PLAN.md
Resume file: None
