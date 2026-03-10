---
phase: 6
slug: dark-mode
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-10
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest (frontend) + pytest 7.x (backend regression) |
| **Config file** | `client/vite.config.ts` (Vitest) / `server/pytest.ini` (pytest) |
| **Quick run command** | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run src/test/darkMode.test.tsx 2>&1 \| tail -20` |
| **Full suite command** | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run 2>&1 \| tail -10` |
| **Estimated runtime** | ~10 seconds |

> **Note:** Phase 6 is pure CSS/HTML. The darkMode.test.tsx file (created in Wave 0) tests DOM structure assertions (meta tag, @media block, token presence). All visual verification (contrast, FOUC, scrollbars) is manual-only.

---

## Sampling Rate

- **After every task commit:** Run quick Vitest darkMode suite
- **After every plan wave:** Run full Vitest suite + manual browser verification
- **Before `/gsd:verify-work`:** Full suite must be green + all manual checks completed
- **Max feedback latency:** ~10 seconds (automated) / immediate (browser visual)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| 06-00-T1 | 06-00 | 0 | DRKM-01,03,04 | unit | `npx vitest run src/test/darkMode.test.tsx \| tail -20` | ⬜ pending |
| 06-01-T1 | 06-01 | 1 | DRKM-01,02 | unit | `npx vitest run src/test/darkMode.test.tsx \| tail -20` | ⬜ pending |
| 06-01-T2 | 06-01 | 1 | DRKM-01,02 | unit | `npx vitest run \| tail -10` | ⬜ pending |
| 06-02-T1 | 06-02 | 2 | DRKM-01,02 | unit | `npx vitest run \| tail -10` | ⬜ pending |
| 06-02-T2 | 06-02 | 2 | DRKM-01,02 | unit | `npx vitest run \| tail -10` | ⬜ pending |
| 06-03-T1 | 06-03 | 2 | DRKM-01,02 | unit | `npx vitest run \| tail -10` | ⬜ pending |
| 06-03-T2 | 06-03 | 2 | DRKM-01,02 | unit | `npx vitest run \| tail -10` | ⬜ pending |
| 06-04-T1 | 06-04 | 3 | DRKM-03,04 | unit | `npx vitest run src/test/darkMode.test.tsx \| tail -20` | ⬜ pending |
| 06-04-T2 | 06-04 | 3 | DRKM-01-04 | manual | `(human verify checkpoint)` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `client/src/test/darkMode.test.tsx` — failing test stubs for DRKM-01, DRKM-03, DRKM-04

*Wave 0 creates the failing test scaffold. All subsequent automated verify commands depend on this file existing.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| OS dark mode switch → app switches live | DRKM-01 | Requires OS preference toggle — CSS media query not automatable | Toggle OS dark mode; verify app switches without page reload |
| All text/bg pairs WCAG AA | DRKM-02 | Requires visual/contrast tool inspection | Use browser DevTools or axe in both themes; all pairs pre-verified in RESEARCH.md |
| No white flash on hard refresh | DRKM-03 | Requires network throttle + hard refresh in dark OS | Enable dark OS, throttle network to Slow 3G, hard refresh; verify no white flash |
| Scrollbars + form inputs themed | DRKM-04 | Browser chrome theming is visual-only | Check scrollbars, text inputs, checkboxes adopt dark chrome in dark OS |
| Context menu hover colors | DRKM-01 | Verifying CSS :hover conversion replaced inline JS handlers | Hover over context menu items in dark mode; verify hover color uses CSS token |
| Chip colors maintain hue identity | DRKM-02 | Visual inspection of chip color families | Verify tag/mention/date chips: same hue family, darkened bg + lightened text |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (06-00 creates darkMode.test.tsx)
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
