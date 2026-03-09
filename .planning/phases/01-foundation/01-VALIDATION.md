---
phase: 1
slug: foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-09
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest (server) + vitest + @testing-library/react (client) |
| **Config file** | None yet — Wave 0 installs and creates `server/vitest.config.ts` |
| **Quick run command** | `cd server && npx vitest run --reporter=verbose` |
| **Full suite command** | `cd server && npx vitest run && cd ../client && npx vitest run` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd server && npx vitest run --reporter=verbose`
- **After every plan wave:** Run `cd server && npx vitest run && cd ../client && npx vitest run`
- **Before `/gsd:verify-work`:** Full suite must be green + Claude manually verifies auth flows on `https://notes.gregorymaingret.fr`
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 0 | AUTH-01–05, DOC-01–07 | setup | `cd server && npx vitest run` | ❌ W0 | ⬜ pending |
| 1-02-01 | 02 | 1 | AUTH-01 | unit | `cd server && npx vitest run server/tests/auth.test.ts` | ❌ W0 | ⬜ pending |
| 1-02-02 | 02 | 1 | AUTH-02 | unit | `cd server && npx vitest run server/tests/auth.test.ts` | ❌ W0 | ⬜ pending |
| 1-02-03 | 02 | 1 | AUTH-03 | unit | `cd server && npx vitest run server/tests/auth.test.ts` | ❌ W0 | ⬜ pending |
| 1-02-04 | 02 | 1 | AUTH-04 | unit | `cd server && npx vitest run server/tests/auth.test.ts` | ❌ W0 | ⬜ pending |
| 1-02-05 | 02 | 1 | AUTH-05 | unit | `cd server && npx vitest run server/tests/auth.test.ts` | ❌ W0 | ⬜ pending |
| 1-03-01 | 03 | 2 | DOC-01 | unit | `cd server && npx vitest run server/tests/documents.test.ts` | ❌ W0 | ⬜ pending |
| 1-03-02 | 03 | 2 | DOC-02 | unit | `cd server && npx vitest run server/tests/documents.test.ts` | ❌ W0 | ⬜ pending |
| 1-03-03 | 03 | 2 | DOC-03 | unit | `cd server && npx vitest run server/tests/documents.test.ts` | ❌ W0 | ⬜ pending |
| 1-03-04 | 03 | 2 | DOC-04 | unit | `cd server && npx vitest run server/tests/documents.test.ts` | ❌ W0 | ⬜ pending |
| 1-03-05 | 03 | 2 | DOC-05 | unit | `cd server && npx vitest run server/tests/documents.test.ts` | ❌ W0 | ⬜ pending |
| 1-03-06 | 03 | 2 | DOC-06 | unit | `cd server && npx vitest run server/tests/documents.test.ts` | ❌ W0 | ⬜ pending |
| 1-03-07 | 03 | 2 | DOC-07 | unit | `cd server && npx vitest run server/tests/documents.test.ts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `server/tests/auth.test.ts` — stubs for AUTH-01 through AUTH-05
- [ ] `server/tests/documents.test.ts` — stubs for DOC-01 through DOC-07
- [ ] `server/tests/helpers/testApp.ts` — Express app factory for test isolation
- [ ] `server/vitest.config.ts` — vitest configuration for server
- [ ] Install: `cd server && npm install -D vitest supertest @types/supertest`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Google SSO login flow | AUTH-03 | Claude cannot use Google OAuth (no browser) | Greg manually logs in with Google on `https://notes.gregorymaingret.fr`; verifies redirect to app with correct account |
| Sidebar drag-reorder persists | DOC-04 | Visual drag-and-drop interaction | Drag document in sidebar; refresh page; verify order persisted |
| Mobile sidebar slide-over | UX decision | Touch gesture on real device | Open app on mobile; tap sidebar toggle; verify slide-over panel behavior |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
