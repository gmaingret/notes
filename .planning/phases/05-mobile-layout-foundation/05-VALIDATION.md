---
phase: 5
slug: mobile-layout-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-10
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 4.x |
| **Config file** | `client/vite.config.ts` (test section) |
| **Quick run command** | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run --reporter=verbose src/test/mobileLayout.test.tsx` |
| **Full suite command** | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd /c/Users/gmain/Dev/Notes/client && npx vitest run --reporter=verbose src/test/mobileLayout.test.tsx`
- **After every plan wave:** Run `cd /c/Users/gmain/Dev/Notes/client && npx vitest run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 5-W0-01 | W0 | 0 | MOBL-01,02,03,04,06,07 | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ W0 | ⬜ pending |
| 5-01-01 | 01 | 1 | MOBL-06 | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ W0 | ⬜ pending |
| 5-01-02 | 01 | 1 | MOBL-04 | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ W0 | ⬜ pending |
| 5-01-03 | 01 | 1 | MOBL-02 | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ W0 | ⬜ pending |
| 5-02-01 | 02 | 1 | MOBL-01 | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ W0 | ⬜ pending |
| 5-02-02 | 02 | 1 | MOBL-03 | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ W0 | ⬜ pending |
| 5-02-03 | 02 | 1 | MOBL-07 | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ W0 | ⬜ pending |
| 5-03-01 | 03 | 2 | MOBL-05 | manual | N/A — browser DevTools / physical device | manual-only | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `client/src/test/mobileLayout.test.tsx` — stubs for MOBL-01, MOBL-02, MOBL-03, MOBL-04, MOBL-06, MOBL-07

*Existing infrastructure (Vitest, RTL, jsdom) covers all automated requirements. Only the test file itself is missing.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| All interactive elements have touch targets ≥44×44px | MOBL-05 | jsdom does not compute layout; element dimensions are not meaningful in unit tests | Open app on mobile viewport (DevTools responsive mode or physical device); use DevTools "Inspect Element" to measure bounding box of hamburger, X button, sidebar header buttons, bullet dot, toolbar icons, and breadcrumb links — each must be ≥44×44px |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
