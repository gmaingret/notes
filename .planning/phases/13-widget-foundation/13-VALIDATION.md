---
phase: 13
slug: widget-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-14
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
| 13-01-01 | 01 | 1 | SETUP-01 | unit (Robolectric) | `./gradlew testDebugUnitTest --tests "*.NotesWidgetReceiverTest" -x lintDebug` | ❌ W0 | ⬜ pending |
| 13-01-02 | 01 | 1 | SETUP-02 | unit (Robolectric) | `./gradlew testDebugUnitTest --tests "*.WidgetConfigViewModelTest" -x lintDebug` | ❌ W0 | ⬜ pending |
| 13-02-01 | 02 | 1 | DISP-01 | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ❌ W0 | ⬜ pending |
| 13-02-02 | 02 | 1 | DISP-02 | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ❌ W0 | ⬜ pending |
| 13-02-03 | 02 | 1 | DISP-03 | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ❌ W0 | ⬜ pending |
| 13-02-04 | 02 | 1 | DISP-04 | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ❌ W0 | ⬜ pending |
| 13-02-05 | 02 | 1 | DISP-05 | unit (pure Kotlin) | `./gradlew testDebugUnitTest --tests "*.NotesWidgetColorSchemeTest" -x lintDebug` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/.../widget/NotesWidgetReceiverTest.kt` — stubs for SETUP-01
- [ ] `app/src/test/.../widget/WidgetConfigViewModelTest.kt` — stubs for SETUP-02
- [ ] `app/src/test/.../widget/NotesWidgetTest.kt` — stubs for DISP-01, DISP-02, DISP-03, DISP-04
- [ ] `app/src/test/.../widget/NotesWidgetColorSchemeTest.kt` — stubs for DISP-05
- [ ] `app/src/test/.../widget/WidgetStateStoreTest.kt` — DataStore read/write/clear

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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
