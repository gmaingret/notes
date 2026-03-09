---
phase: 4
slug: attachments-comments-and-mobile
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-09
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest 4.x (client) + vitest 3.x (server) |
| **Config file** | `client/vite.config.ts` (test section) + `server/vitest.config.ts` |
| **Quick run command** | `cd client && npx vitest run --reporter=verbose` or `cd server && npx vitest run` |
| **Full suite command** | Both: `cd client && npx vitest run` + `cd server && npx vitest run` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run relevant test file only (e.g., `npx vitest run src/test/swipeGesture.test.ts`)
- **After every plan wave:** `cd client && npx vitest run` + `cd server && npx vitest run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 4-01-* | 01 | 0 | ATT-01, ATT-02, ATT-06, CMT-03 | unit stubs | `cd server && npx vitest run tests/attachments.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-01-* | 01 | 0 | ATT-03, ATT-05, CMT-01, CMT-02, CMT-04 | unit stubs | `cd client && npx vitest run src/test/attachmentRow.test.tsx src/test/noteRow.test.tsx` | ❌ Wave 0 | ⬜ pending |
| 4-01-* | 01 | 0 | ATT-04 | unit stub | `cd client && npx vitest run src/test/pdfThumbnail.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-01-* | 01 | 0 | MOB-01, MOB-02 | unit stub | `cd client && npx vitest run src/test/swipeGesture.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-01-* | 01 | 0 | BULL-16, MOB-03 | unit stub | `cd client && npx vitest run src/test/longPress.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-01-* | 01 | 0 | MOB-05 | unit stub | `cd client && npx vitest run src/test/keyboardOffset.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-02-* | 02 | 1 | ATT-01, ATT-06 | unit | `cd server && npx vitest run tests/attachments.test.ts -x` | ❌ Wave 0 | ⬜ pending |
| 4-03-* | 03 | 1 | ATT-02, ATT-03, ATT-04, ATT-05 | unit | `cd client && npx vitest run src/test/attachmentRow.test.tsx src/test/pdfThumbnail.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-04-* | 04 | 1 | CMT-01, CMT-02, CMT-03, CMT-04 | unit | `cd client && npx vitest run src/test/noteRow.test.tsx` + `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-05-* | 05 | 2 | MOB-01, MOB-02, BULL-16, MOB-03 | unit | `cd client && npx vitest run src/test/swipeGesture.test.ts src/test/longPress.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-06-* | 06 | 2 | MOB-05 | unit | `cd client && npx vitest run src/test/keyboardOffset.test.ts` | ❌ Wave 0 | ⬜ pending |
| 4-07-* | 07 | 3 | MOB-04 | manual | Manual test on mobile device | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `server/tests/attachments.test.ts` — stubs for ATT-01, ATT-02, ATT-06 (upload, list, delete, disk path)
- [ ] `client/src/test/attachmentRow.test.tsx` — stubs for ATT-03, ATT-05 (image/other rendering)
- [ ] `client/src/test/pdfThumbnail.test.ts` — stub for ATT-04 (mock pdfjsLib)
- [ ] `client/src/test/noteRow.test.tsx` — stubs for CMT-01, CMT-02, CMT-04 (note display, focus, clear)
- [ ] `client/src/test/swipeGesture.test.ts` — stubs for MOB-01, MOB-02 (threshold logic)
- [ ] `client/src/test/longPress.test.ts` — stubs for BULL-16, MOB-03 (timer logic)
- [ ] `client/src/test/keyboardOffset.test.ts` — stub for MOB-05 (visualViewport offset calculation)
- [ ] Extend `server/tests/bullets.test.ts` — stub for CMT-03 (note patch endpoint)

Framework install: none needed — vitest already installed on both client and server.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Touch drag reorders bullets on mobile | MOB-04 | dnd-kit PointerSensor — jsdom cannot simulate real touch drag | On mobile device: long-press drag dot, drag bullet to new position, verify order persists |
| iOS visualViewport toolbar positioning | MOB-05 | Real iOS 17/18 visualViewport bug; jsdom cannot replicate | On iPhone: tap bullet to open keyboard, verify toolbar stays visible above keyboard; dismiss keyboard, verify toolbar resets |
| Image renders inline after upload | ATT-03 | Object URL lifecycle requires real browser | Upload image file, verify thumbnail renders in bullet row |
| PDF thumbnail renders | ATT-04 | pdfjs-dist worker requires real browser environment | Upload PDF, verify thumbnail renders in bullet row |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
