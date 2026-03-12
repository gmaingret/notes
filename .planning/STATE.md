---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Native Android Client
status: ready_to_plan
stopped_at: Completed 09-03-PLAN.md
last_updated: "2026-03-12T09:25:39.561Z"
last_activity: 2026-03-12 — v2.0 roadmap created, phases 9-12 defined
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 5
  completed_plans: 3
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

### Pending Todos

None.

### Blockers/Concerns

- Phase 9: Refresh cookie persistence across process death — JavaNetCookieJar holds cookies in memory only; DataStore/Tink serialization wrapper must be written and validated against production server cookie format as the very first Phase 9 deliverable
- Phase 11 tech spike: ComputeDragProjectionUseCase has no Android equivalent for getBoundingClientRect — confirm Calvin-LL/Reorderable library fits flat-tree model before committing to drag architecture
- Phase 11 UX decision: Gboard Tab key interception by OEM keyboards — toolbar indent/outdent buttons must be the PRIMARY interaction path for indent/outdent; physical keyboard Tab is secondary

## Session Continuity

Last session: 2026-03-12T09:25:39.558Z
Stopped at: Completed 09-03-PLAN.md
Resume file: None
