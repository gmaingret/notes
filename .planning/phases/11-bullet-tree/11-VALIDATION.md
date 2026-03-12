---
phase: 11
slug: bullet-tree
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-12
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK 1.13.14 + coroutines-test 1.10.1 |
| **Config file** | android/app/build.gradle.kts (testOptions.unitTests.isIncludeAndroidResources = true) |
| **Quick run command** | `./gradlew testDebugUnitTest --tests "*.Bullet*"` (from android/ directory) |
| **Full suite command** | `./gradlew testDebugUnitTest` (from android/ directory) |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew testDebugUnitTest --tests "*.Bullet*"`
- **After every plan wave:** Run `./gradlew testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | TREE-01 | unit | `./gradlew testDebugUnitTest --tests "*.FlattenTreeUseCaseTest"` | ❌ W0 | ⬜ pending |
| 11-01-02 | 01 | 1 | TREE-01 | unit | same | ❌ W0 | ⬜ pending |
| 11-01-03 | 01 | 1 | TREE-01 | unit | same | ❌ W0 | ⬜ pending |
| 11-02-01 | 02 | 1 | TREE-02 | unit | `./gradlew testDebugUnitTest --tests "*.BulletTreeViewModelTest"` | ❌ W0 | ⬜ pending |
| 11-02-02 | 02 | 1 | TREE-02 | unit | same | ❌ W0 | ⬜ pending |
| 11-03-01 | 03 | 1 | TREE-03 | unit | same | ❌ W0 | ⬜ pending |
| 11-04-01 | 04 | 1 | TREE-04 | unit | same | ❌ W0 | ⬜ pending |
| 11-05-01 | 05 | 2 | TREE-05 | unit | same | ❌ W0 | ⬜ pending |
| 11-06-01 | 06 | 2 | TREE-06 | unit | same | ❌ W0 | ⬜ pending |
| 11-08-01 | 08 | 2 | TREE-08 | unit | same | ❌ W0 | ⬜ pending |
| 11-09-01 | 09 | 3 | TREE-09 | unit | same | ❌ W0 | ⬜ pending |
| 11-10-01 | 10 | 3 | TREE-10 | unit | same | ❌ W0 | ⬜ pending |
| 11-11-01 | 11 | 2 | CONT-01 | unit | `./gradlew testDebugUnitTest --tests "*.BulletMarkdownRendererTest"` | ❌ W0 | ⬜ pending |
| 11-11-02 | 11 | 2 | CONT-02 | unit | same | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/FlattenTreeUseCaseTest.kt` — stubs for TREE-01 (zoom, collapse, depth)
- [ ] `android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModelTest.kt` — stubs for TREE-02 through TREE-10
- [ ] `android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletMarkdownRendererTest.kt` — stubs for CONT-01, CONT-02
- No framework install needed — existing JUnit4 + MockK + coroutines-test infrastructure covers all tests

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Enter key intercept on Gboard | TREE-02 | IME behavior varies by keyboard; `onValueChange` `\n` detection needs device validation | 1. Open a document on physical device 2. Tap a bullet to edit 3. Type text and press Enter — should create sibling below 4. Press Enter on empty bullet — should outdent |
| Drag reparenting UX | TREE-09 | Horizontal drag threshold is empirical; needs device testing | 1. Long-press a bullet to start drag 2. Move horizontally while dragging — should change depth indicator 3. Drop at new position — verify reparenting worked |
| Auto-scroll with keyboard | TREE-07 | BringIntoViewRequester behavior depends on IME timing | 1. Open document with many bullets 2. Tap bottom bullet to edit — keyboard should push content up 3. Focused bullet should remain visible |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
