---
phase: 8
slug: swipe-polish-and-quick-open-palette
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-11
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest + @testing-library/react (jsdom) |
| **Config file** | `client/vite.config.ts` (test section) |
| **Quick run command** | `cd /c/Users/gmain/dev/Notes/client && npx vitest run --reporter=verbose` |
| **Full suite command** | `cd /c/Users/gmain/dev/Notes/client && npx vitest run` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd /c/Users/gmain/dev/Notes/client && npx vitest run`
- **After every plan wave:** Run `cd /c/Users/gmain/dev/Notes/client && npx vitest run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 8-01-01 | 01 | 0 | GEST-01..05 | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ W0 | ⬜ pending |
| 8-01-02 | 01 | 0 | QKOP-01..07 | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ W0 | ⬜ pending |
| 8-02-01 | 02 | 1 | GEST-01 | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ W0 | ⬜ pending |
| 8-02-02 | 02 | 1 | GEST-02 | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ W0 | ⬜ pending |
| 8-02-03 | 02 | 1 | GEST-03 | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ W0 | ⬜ pending |
| 8-02-04 | 02 | 1 | GEST-04 | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ W0 | ⬜ pending |
| 8-02-05 | 02 | 1 | GEST-05 | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ W0 | ⬜ pending |
| 8-03-01 | 03 | 1 | QKOP-01 | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ W0 | ⬜ pending |
| 8-03-02 | 03 | 1 | QKOP-02 | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ W0 | ⬜ pending |
| 8-03-03 | 03 | 1 | QKOP-03 | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ W0 | ⬜ pending |
| 8-03-04 | 03 | 1 | QKOP-04 | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ W0 | ⬜ pending |
| 8-03-05 | 03 | 1 | QKOP-05 | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ W0 | ⬜ pending |
| 8-03-06 | 03 | 1 | QKOP-06 | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ W0 | ⬜ pending |
| 8-03-07 | 03 | 1 | QKOP-07 | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `client/src/test/swipePolish.test.ts` — stubs for GEST-01, GEST-02, GEST-03, GEST-04, GEST-05
- [ ] `client/src/test/quickOpenPalette.test.ts` — stubs for QKOP-01, QKOP-02, QKOP-03, QKOP-04, QKOP-05, QKOP-06, QKOP-07

*Tests follow source-inspection pattern established in phases 6, 7, 7.1 — using `readFileSync` to assert on code structure, avoiding extensive jsdom mocking overhead.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Swipe animation feel (momentum, easing quality) | GEST-03, GEST-04 | CSS transitions cannot be meaningfully tested in jsdom | On mobile: swipe right past threshold, observe ease-out snap-back; swipe past commit threshold, observe row slide-out before removal |
| Mobile palette trigger (header button) | QKOP-01 | Touch tap on button requires real browser | On mobile: tap search button in DocumentView header, verify palette opens |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
