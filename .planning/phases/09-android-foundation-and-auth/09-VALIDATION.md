---
phase: 9
slug: android-foundation-and-auth
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-12
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + MockK 1.13.x + Robolectric 4.16 + androidx.compose.ui:ui-test-junit4 |
| **Config file** | android/app/build.gradle.kts (testOptions.unitTests.isIncludeAndroidResources = true) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest` (from android/ directory) |
| **Full suite command** | `./gradlew test` (from android/ directory) |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.{RelevantTestClass}"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | AUTH-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.RegisterUseCaseTest"` | ❌ W0 | ⬜ pending |
| 09-01-02 | 01 | 1 | AUTH-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.LoginUseCaseTest"` | ❌ W0 | ⬜ pending |
| 09-01-03 | 01 | 1 | AUTH-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.GoogleSignInUseCaseTest"` | ❌ W0 | ⬜ pending |
| 09-01-04 | 01 | 1 | AUTH-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.AuthInterceptorTest"` | ❌ W0 | ⬜ pending |
| 09-01-05 | 01 | 1 | AUTH-05 | unit | `./gradlew :app:testDebugUnitTest --tests "*.TokenAuthenticatorTest"` | ❌ W0 | ⬜ pending |
| 09-01-06 | 01 | 1 | AUTH-06 | unit | `./gradlew :app:testDebugUnitTest --tests "*.SplashViewModelTest"` | ❌ W0 | ⬜ pending |
| 09-02-01 | 02 | 1 | AUTH-01/02 | compose UI | `./gradlew :app:testDebugUnitTest --tests "*.AuthScreenTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/RegisterUseCaseTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/LoginUseCaseTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/GoogleSignInUseCaseTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/SplashViewModelTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/data/remote/interceptor/AuthInterceptorTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/data/remote/interceptor/TokenAuthenticatorTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/presentation/auth/AuthScreenTest.kt`
- [ ] Android project scaffold — entire `/android/` directory does not yet exist
- [ ] Framework install: `./gradlew wrapper --gradle-version=8.13` + AGP 8.9.0

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Google SSO bottom sheet picker | AUTH-03 | Credential Manager requires device with Play Services | 1. Install debug APK on device 2. Tap "Sign in with Google" 3. Verify bottom sheet shows 4. Select account 5. Verify transition to main screen |
| Cold start silent re-auth | AUTH-06 | Requires process death + restart cycle | 1. Log in 2. Force-stop app 3. Relaunch 4. Verify main screen without login prompt |
| Edge-to-edge rendering | App Identity | Visual verification needed | 1. Launch app 2. Verify status bar and nav bar are transparent with content behind |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
