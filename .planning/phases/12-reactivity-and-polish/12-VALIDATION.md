---
phase: 12
slug: reactivity-and-polish
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-12
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK 1.13.14 + Coroutines Test 1.10.1 |
| **Config file** | `android/app/src/test/` (JVM unit tests) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "com.gmaingret.notes.presentation.bullet.*" -x lint` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest -x lint` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest -x lint`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest -x lint`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 12-01-01 | 01 | 1 | CONT-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*AttachmentRepositoryTest*"` | ❌ W0 | ⬜ pending |
| 12-01-02 | 01 | 1 | CONT-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*BookmarksViewModelTest*"` | ❌ W0 | ⬜ pending |
| 12-01-03 | 01 | 1 | POLL-05 | unit | `./gradlew :app:testDebugUnitTest --tests "*SearchViewModelTest*"` | ❌ W0 | ⬜ pending |
| 12-02-01 | 02 | 1 | POLL-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*BulletTreeViewModelTest*"` | ✅ | ⬜ pending |
| 12-02-02 | 02 | 1 | POLL-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*BulletTreeViewModelTest*"` | ✅ | ⬜ pending |
| 12-03-01 | 03 | 2 | POLL-04 | manual | n/a — gesture UI | manual | ⬜ pending |
| 12-04-01 | 04 | 2 | POLL-06 | unit | `./gradlew :app:testDebugUnitTest --tests "*BulletTreeViewModelTest*"` | ✅ | ⬜ pending |
| 12-05-01 | 05 | 3 | POLL-07 | manual | n/a — visual check | manual | ⬜ pending |
| 12-05-02 | 05 | 3 | POLL-08 | manual | n/a — animation check | manual | ⬜ pending |
| 12-06-01 | 06 | 3 | POLL-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*BulletTreeViewModelTest*"` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/.../data/repository/AttachmentRepositoryTest.kt` — stubs for CONT-03
- [ ] `app/src/test/.../presentation/bookmarks/BookmarksViewModelTest.kt` — stubs for CONT-04
- [ ] `app/src/test/.../presentation/search/SearchViewModelTest.kt` — stubs for POLL-05

*Existing infrastructure covers POLL-01, POLL-02, POLL-03, POLL-06 via BulletTreeViewModelTest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Swipe right = complete, swipe left = delete with proportional color/icon | POLL-04 | Gesture interaction not unit-testable | Swipe right on bullet → green bg + check icon; swipe left → red bg + trash icon; verify disabled when focused |
| Dark theme colors consistent across all screens | POLL-07 | Visual verification | Toggle system dark mode; verify all screens render with DarkColorScheme |
| AnimatedVisibility / animateItem / Crossfade applied | POLL-08 | Animation visual check | Add/remove bullets, expand/collapse, toggle search bar → verify smooth transitions |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
