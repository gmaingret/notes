---
phase: 7
slug: icons-fonts-and-pwa
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-10
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.0.18 + jsdom + @testing-library/react 16 |
| **Config file** | `client/vite.config.ts` (`test.environment: 'jsdom'`, `test.setupFiles: ['./src/test/setup.ts']`) |
| **Quick run command** | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts` |
| **Full suite command** | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd /c/Users/gmain/Dev/Notes/client && npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts`
- **After every plan wave:** Run `cd /c/Users/gmain/Dev/Notes/client && npx vitest run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 7-01-01 | 01 | 0 | VISL-01, VISL-02, VISL-03, PWA-01, PWA-02, PWA-03 | unit (file read) | `npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts` | ❌ W0 | ⬜ pending |
| 7-02-01 | 02 | 1 | VISL-01 | unit (file read) | `npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts` | ❌ W0 | ⬜ pending |
| 7-02-02 | 02 | 1 | VISL-01 | unit (file read) | `npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts` | ❌ W0 | ⬜ pending |
| 7-02-03 | 02 | 1 | VISL-01 | unit (file read) | `npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts` | ❌ W0 | ⬜ pending |
| 7-03-01 | 03 | 2 | VISL-02, VISL-03 | unit (file read) | `npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts` | ❌ W0 | ⬜ pending |
| 7-04-01 | 04 | 3 | PWA-01, PWA-02, PWA-03 | unit (file read) | `npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `client/src/test/iconsAndFonts.test.ts` — stubs for VISL-01, VISL-02, VISL-03, PWA-01, PWA-02, PWA-03

*Existing setup.ts and test infrastructure are sufficient — no new framework installation needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Add to Home Screen prompt appears on Chrome Android | PWA-01 | Requires physical device + Chrome Android | Open app in Chrome Android, tap browser menu, verify "Add to Home Screen" option appears |
| App opens without browser chrome after installation | PWA-03 | Requires physical device or emulator | Install PWA on device, launch from home screen, verify no browser address bar/chrome |
| Add to Home Screen available on iOS Safari | PWA-01 | iOS Safari never shows automatic prompt | Open in Safari on iOS, tap Share, verify "Add to Home Screen" option appears |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
