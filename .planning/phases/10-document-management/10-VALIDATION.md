---
phase: 10
slug: document-management
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-12
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + MockK 1.13.14 + Kotlin Coroutines Test 1.10.1 |
| **Config file** | `android/app/build.gradle.kts` (testOptions.unitTests.isIncludeAndroidResources = true) |
| **Quick run command** | `./gradlew testDebugUnitTest --tests "*.document.*"` (from `android/`) |
| **Full suite command** | `./gradlew testDebugUnitTest` (from `android/`) |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew testDebugUnitTest --tests "*.document.*" --tests "*.DocumentRepository*"` (from `android/`)
- **After every plan wave:** Run `./gradlew testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 10-01-01 | 01 | 1 | DOCM-01 | unit | `./gradlew testDebugUnitTest --tests "*.GetDocumentsUseCaseTest"` | ❌ W0 | ⬜ pending |
| 10-01-02 | 01 | 1 | DOCM-02 | unit | `./gradlew testDebugUnitTest --tests "*.CreateDocumentUseCaseTest"` | ❌ W0 | ⬜ pending |
| 10-01-03 | 01 | 1 | DOCM-03 | unit | `./gradlew testDebugUnitTest --tests "*.RenameDocumentUseCaseTest"` | ❌ W0 | ⬜ pending |
| 10-01-04 | 01 | 1 | DOCM-04 | unit | `./gradlew testDebugUnitTest --tests "*.DeleteDocumentUseCaseTest"` | ❌ W0 | ⬜ pending |
| 10-01-05 | 01 | 1 | DOCM-05 | unit | `./gradlew testDebugUnitTest --tests "*.ReorderDocumentUseCaseTest"` | ❌ W0 | ⬜ pending |
| 10-01-06 | 01 | 1 | DOCM-06 | unit | `./gradlew testDebugUnitTest --tests "*.OpenDocumentUseCaseTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/test/.../domain/usecase/GetDocumentsUseCaseTest.kt` — stubs for DOCM-01
- [ ] `android/app/src/test/.../domain/usecase/CreateDocumentUseCaseTest.kt` — stubs for DOCM-02
- [ ] `android/app/src/test/.../domain/usecase/RenameDocumentUseCaseTest.kt` — stubs for DOCM-03
- [ ] `android/app/src/test/.../domain/usecase/DeleteDocumentUseCaseTest.kt` — stubs for DOCM-04
- [ ] `android/app/src/test/.../domain/usecase/ReorderDocumentUseCaseTest.kt` — stubs for DOCM-05
- [ ] `android/app/src/test/.../domain/usecase/OpenDocumentUseCaseTest.kt` — stubs for DOCM-06

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Drawer opens via hamburger tap, swipe disabled | DOCM-01 | UI gesture testing requires instrumented test or manual | Open app → verify no left-edge swipe → tap hamburger → drawer opens |
| Drag-reorder with haptic feedback | DOCM-05 | Haptic feedback requires physical device | Long-press document row → feel vibration → drag to new position → verify order persists |
| Skeleton shimmer loading state | DOCM-01 | Visual animation verification | Open drawer on slow network → verify shimmer rows appear |
| Cold start restores last document | DOCM-06 | Requires app process kill | Open doc → force-stop app → relaunch → verify same doc opens |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
