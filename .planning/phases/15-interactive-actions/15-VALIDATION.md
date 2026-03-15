---
phase: 15
slug: interactive-actions
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-14
validated: 2026-03-15
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK + Kotlin Coroutines Test |
| **Config file** | `android/app/build.gradle.kts` (testImplementation blocks) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.widget.*" -x lintDebug` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest -x lintDebug` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.widget.*" -x lintDebug`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest -x lintDebug`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 15-01-01 | 01 | 1 | ACT-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DeleteBulletActionCallbackTest" -x lintDebug` | ✅ | ✅ green |
| 15-01-02 | 01 | 1 | ACT-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DeleteBulletActionCallbackTest" -x lintDebug` | ✅ | ✅ green |
| 15-02-01 | 02 | 1 | ACT-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.AddBulletActionTest" -x lintDebug` | ✅ | ✅ green |
| 15-02-02 | 02 | 1 | ACT-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.AddBulletActionTest" -x lintDebug` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Test Coverage Detail

### ACT-01 — `AddBulletActionTest.kt` (7 tests)
- `performAddBullet calls CreateBulletUseCase with correct CreateBulletRequest`
- `on CreateBulletUseCase success, temp bullet is replaced with server-assigned ID`
- `on CreateBulletUseCase failure, original bullet list is restored to WidgetStateStore`
- `on auth error 401, display state transitions to SESSION_EXPIRED`
- `optimistic add inserts new WidgetBullet at position 0 (top of list)`
- `after successful add, display state is CONTENT even if list was previously empty`
- `pushStateToGlance is called immediately after optimistic insert`

### ACT-02 — `DeleteBulletActionCallbackTest.kt` (5 tests)
- `on successful delete, returns null (no toast) and does not rollback`
- `calls DeleteBulletUseCase with the correct bulletId`
- `on DeleteBulletUseCase failure, original bullet list is restored to WidgetStateStore`
- `on auth error, display state transitions to SESSION_EXPIRED`
- `with null bulletId returns null without side effects`

---

## Wave 0 Requirements

- [x] `android/app/src/test/.../widget/DeleteBulletActionCallbackTest.kt` — 5 tests for ACT-02
- [x] `android/app/src/test/.../widget/add/AddBulletActionTest.kt` — 7 tests for ACT-01

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| [+] button opens overlay dialog on home screen | ACT-01 | Requires physical launcher interaction | Tap [+] on widget, verify transparent overlay appears with focused text field |
| Enter key creates bullet and closes dialog | ACT-01 | Requires keyboard/IME interaction | Type text, press Enter, verify dialog closes and bullet appears in widget |
| Tap outside dismisses dialog | ACT-01 | Requires touch event on dimmed background | Tap dimmed area outside dialog, verify it dismisses |
| Delete icon tap removes bullet from widget | ACT-02 | Requires widget interaction on launcher | Tap x icon on bullet row, verify bullet disappears immediately |

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

Both requirements already had full automated test coverage (12 tests across 2 test files). No new tests needed.
