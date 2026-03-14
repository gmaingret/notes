---
phase: 15
slug: interactive-actions
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-14
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK + Kotlin Coroutines Test |
| **Config file** | `android/app/build.gradle.kts` (testImplementation blocks) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.widget.*"` |
| **Full suite command** | `ssh root@192.168.1.50 "cd /root/notes/android && ./gradlew :app:testDebugUnitTest"` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.widget.*"`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 15-01-01 | 01 | 1 | ACT-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DeleteBulletActionCallbackTest"` | ❌ W0 | ⬜ pending |
| 15-01-02 | 01 | 1 | ACT-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DeleteBulletActionCallbackTest"` | ❌ W0 | ⬜ pending |
| 15-02-01 | 02 | 1 | ACT-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.AddBulletActivityTest"` | ❌ W0 | ⬜ pending |
| 15-02-02 | 02 | 1 | ACT-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.AddBulletActivityTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/test/.../widget/DeleteBulletActionCallbackTest.kt` — stubs for ACT-02 (optimistic remove, rollback, auth error)
- [ ] `android/app/src/test/.../widget/add/AddBulletActivityTest.kt` — stubs for ACT-01 (optimistic insert, rollback, temp ID replacement)

*Existing test infrastructure (JUnit 4, MockK, Coroutines Test) already installed.*

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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
