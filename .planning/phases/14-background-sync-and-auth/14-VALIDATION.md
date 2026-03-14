---
phase: 14
slug: background-sync-and-auth
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-14
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK 1.13.14 + Robolectric 4.16 |
| **Config file** | Robolectric via `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [28])` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.widget.*" -x lint` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest -x lint` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.widget.*" -x lint`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest -x lint`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 14-01-01 | 01 | 0 | SYNC-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncTriggerTest" -x lint` | ❌ W0 | ⬜ pending |
| 14-01-02 | 01 | 0 | SYNC-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncWorkerTest" -x lint` | ❌ W0 | ⬜ pending |
| 14-01-03 | 01 | 0 | SYNC-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncTriggerTest" -x lint` | ❌ W0 | ⬜ pending |
| 14-02-01 | 02 | 1 | SYNC-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncTriggerTest" -x lint` | ❌ W0 | ⬜ pending |
| 14-02-02 | 02 | 1 | SYNC-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncWorkerTest" -x lint` | ❌ W0 | ⬜ pending |
| 14-03-01 | 03 | 2 | SYNC-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncTriggerTest" -x lint` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/.../widget/sync/WidgetSyncWorkerTest.kt` — stubs for SYNC-02, SYNC-03 auth
- [ ] `app/src/test/.../widget/sync/WidgetSyncTriggerTest.kt` — stubs for SYNC-01
- [ ] `work-testing` dependency: `testImplementation("androidx.work:work-testing:2.11.1")`

*Use `TestListenableWorkerBuilder` with custom `WorkerFactory` that injects MockK mocks.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Widget updates on home screen after bullet edit | SYNC-01 | Requires real Android launcher | Edit bullet in app, observe widget refresh within 5s |
| Widget shows current content after overnight force-stop | SYNC-02 | Requires real device over time | Force-stop app, wait 15+ min, check widget |
| Widget loads after device reboot without sign-in prompt | SYNC-03 | Requires real device reboot | Reboot device, check widget shows content |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
