---
phase: 13
slug: widget-foundation
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-14
validated: 2026-03-15
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK 1.13.14 + Robolectric 4.16 (existing) |
| **Config file** | `testOptions.unitTests.isIncludeAndroidResources = true` in `build.gradle.kts` |
| **Quick run command** | `./gradlew testDebugUnitTest --tests "com.gmaingret.notes.widget.*" -x lintDebug` |
| **Full suite command** | `./gradlew testDebugUnitTest -x lintDebug` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew testDebugUnitTest --tests "com.gmaingret.notes.widget.*" -x lintDebug`
- **After every plan wave:** Run `./gradlew testDebugUnitTest -x lintDebug`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 13-01-01 | 01 | 1 | SETUP-01 | unit (Robolectric) | `./gradlew testDebugUnitTest --tests "*.NotesWidgetReceiverTest" -x lintDebug` | ✅ | ✅ green |
| 13-01-02 | 01 | 1 | SETUP-02 | unit | `./gradlew testDebugUnitTest --tests "*.WidgetConfigViewModelTest" -x lintDebug` | ✅ | ✅ green |
| 13-02-01 | 02 | 1 | DISP-01 | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ✅ | ✅ green |
| 13-02-02 | 02 | 1 | DISP-02 | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ✅ | ✅ green |
| 13-02-03 | 02 | 1 | DISP-03 | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ✅ | ✅ green |
| 13-02-04 | 02 | 1 | DISP-04 | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ✅ | ✅ green |
| 13-02-05 | 02 | 1 | DISP-05 | unit (Robolectric) | `./gradlew testDebugUnitTest --tests "*.NotesWidgetColorSchemeTest" -x lintDebug` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `app/src/test/.../widget/NotesWidgetReceiverTest.kt` — 2 tests for SETUP-01
- [x] `app/src/test/.../widget/config/WidgetConfigViewModelTest.kt` — 8 tests for SETUP-02
- [x] `app/src/test/.../widget/NotesWidgetTest.kt` — 10 tests for DISP-01, DISP-02, DISP-03, DISP-04
- [x] `app/src/test/.../widget/NotesWidgetColorSchemeTest.kt` — 1 test for DISP-05
- [x] `app/src/test/.../widget/WidgetStateStoreTest.kt` — 12 tests for DataStore read/write/clear
- [x] `app/src/test/.../widget/WidgetContentHelperTest.kt` — 4 tests for markdown stripping

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Widget placement on home screen | SETUP-01 | Requires physical launcher interaction | Long-press home → add widget → verify Notes widget appears in list |
| Visual layout matches Material 3 | DISP-05 | Visual correctness needs human eye | Place widget → verify rounded card, correct colors in light/dark mode |
| Shimmer/loading state appearance | DISP-04 | Static placeholder visual check | Force loading state → verify gray placeholder rows display |
| Scroll behavior in widget | DISP-02 | Glance LazyColumn on real device | Add 10+ bullets → verify widget scrolls |

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
| Gaps found | 1 |
| Resolved | 1 |
| Escalated | 0 |

**Gap resolved:** DISP-04 — added 2 tests for generic (non-401) network error (`IOException` and 500 `HttpException`) returning `WidgetUiState.Error`.
