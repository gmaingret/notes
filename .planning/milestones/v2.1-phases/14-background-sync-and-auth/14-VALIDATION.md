---
phase: 14
slug: background-sync-and-auth
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-14
validated: 2026-03-15
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK 1.13.14 + Robolectric 4.16 |
| **Config file** | Robolectric via `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [28])` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.*" -x lintDebug` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest -x lintDebug` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.*" -x lintDebug`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest -x lintDebug`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 14-01-01 | 01 | 0 | SYNC-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncTriggerTest" -x lintDebug` | ✅ | ✅ green |
| 14-01-02 | 01 | 0 | SYNC-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncWorkerTest" -x lintDebug` | ✅ | ✅ green |
| 14-01-03 | 01 | 0 | SYNC-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncWorkerTest" -x lintDebug` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Test Coverage Detail

### SYNC-01 — `WidgetSyncTriggerTest.kt` (3 tests)
- `refreshWidgetIfDocMatches - writes bullets and CONTENT state when docId matches`
- `refreshWidgetIfDocMatches - does NOT write cache when docId does not match`
- `refreshWidgetIfDocMatches - does NOT write cache when no widget configured`

### SYNC-02 — `WidgetSyncWorkerTest.kt` (6 tests)
- `doWork with no configured docId returns success without calling repositories`
- `doWork with valid docId fetches bullets writes to store returns success`
- `doWork with valid docId but empty bullets writes DisplayState EMPTY`
- `doWork on network failure keeps cached data returns success`
- `doWork on 401 error writes DisplayState SESSION_EXPIRED returns success`
- `doWork with deleted document writes DisplayState DOCUMENT_NOT_FOUND returns success`

### SYNC-03 — `WidgetSyncWorkerTest.kt` (tests 4+5)
- Test 5: 401 auth error → writes SESSION_EXPIRED, returns success
- Test 4: Non-auth network failure → preserves cached data (no overwrite)

---

## Wave 0 Requirements

- [x] `app/src/test/.../widget/sync/WidgetSyncWorkerTest.kt` — 6 tests for SYNC-02, SYNC-03
- [x] `app/src/test/.../widget/sync/WidgetSyncTriggerTest.kt` — 3 tests for SYNC-01
- [x] `work-testing` dependency present in `build.gradle.kts`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Widget updates on home screen after bullet edit | SYNC-01 | Requires real Android launcher | Edit bullet in app, observe widget refresh within 5s |
| Widget shows current content after overnight force-stop | SYNC-02 | Requires real device over time | Force-stop app, wait 15+ min, check widget |
| Widget loads after device reboot without sign-in prompt | SYNC-03 | Requires real device reboot | Reboot device, check widget shows content |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** complete

---

## Validation Audit 2026-03-15

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

All 3 requirements already had full automated test coverage (9 tests across 2 test files). No new tests needed.
