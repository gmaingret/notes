---
phase: 6
slug: dark-mode
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-10
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | pytest 7.x (backend) — no frontend test framework for CSS |
| **Config file** | `server/pytest.ini` |
| **Quick run command** | `ssh root@192.168.1.50 "cd /root/notes/server && .venv/bin/pytest --tb=short -q"` |
| **Full suite command** | `ssh root@192.168.1.50 "cd /root/notes/server && .venv/bin/pytest --tb=short -q"` |
| **Estimated runtime** | ~10 seconds |

> **Note:** Phase 6 is pure CSS/HTML — no new backend tests needed. All dark-mode verification is visual/browser-based (manual). Backend suite runs as regression guard only.

---

## Sampling Rate

- **After every task commit:** Run quick backend suite (regression check)
- **After every plan wave:** Run full suite + manual browser verification
- **Before `/gsd:verify-work`:** Full suite must be green + all manual checks completed
- **Max feedback latency:** 30 seconds (backend) / immediate (browser visual check)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 6-01-01 | 01 | 0 | DRKM-01, DRKM-02 | manual | `(visual)` | ❌ W0 | ⬜ pending |
| 6-01-02 | 01 | 0 | DRKM-02 | manual | `(visual)` | ❌ W0 | ⬜ pending |
| 6-01-03 | 01 | 1 | DRKM-01 | manual | `(visual: toggle OS dark mode)` | N/A | ⬜ pending |
| 6-01-04 | 01 | 1 | DRKM-02 | manual | `(visual: check all text contrast)` | N/A | ⬜ pending |
| 6-02-01 | 02 | 2 | DRKM-01 | manual | `(visual: all components themed)` | N/A | ⬜ pending |
| 6-02-02 | 02 | 3 | DRKM-03 | manual | `(hard refresh in dark OS)` | N/A | ⬜ pending |
| 6-02-03 | 02 | 3 | DRKM-04 | manual | `(visual: scrollbars + inputs)` | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Token block in `client/src/index.css` — CSS custom properties for all color tokens (light values)
- [ ] CSS classes for all component color-bearing elements (structural setup)

*Wave 0 establishes the token foundation and class structure before dark values are added.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| OS dark mode switch → app switches live | DRKM-01 | Requires OS preference toggle — no automated CSS media query test | Toggle OS dark mode; verify app switches without page reload |
| All text/bg pairs WCAG AA | DRKM-02 | Requires visual/contrast tool inspection | Use browser DevTools accessibility checker or axe in both themes |
| No white flash on hard refresh | DRKM-03 | Requires network throttle + hard refresh in dark OS | Enable dark OS, throttle network to Slow 3G, hard refresh; verify no white flash |
| Scrollbars + form inputs themed | DRKM-04 | Browser chrome theming is visual-only | Check scrollbars, text inputs, checkboxes adopt dark chrome in dark OS |
| Context menu hover colors | DRKM-01 | inline onMouseEnter/Leave overrides — verifying CSS conversion works | Hover over context menu items in dark mode; verify hover color uses token |
| Chip colors maintain hue identity | DRKM-02 | Requires visual inspection of chip families | Verify tag/mention/date chips: same hue family, darkened bg + lightened text |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
