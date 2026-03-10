---
phase: 3
slug: rich-content
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-09
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest (client: ^4.0.18, server: ^3.0.8) |
| **Config file** | `client/vite.config.ts` (test property), `server/vitest.config.ts` |
| **Quick run command (client)** | `cd client && npx vitest run` |
| **Quick run command (server)** | `cd server && npx vitest run` |
| **Full suite command** | `cd client && npx vitest run && cd ../server && npx vitest run` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run the specific new test file for that task
- **After every plan wave:** Run full client + server suite
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-W0-01 | W0 | 0 | BULL-09 | unit | `cd client && npx vitest run src/test/markdown.test.ts` | ❌ W0 | ⬜ pending |
| 03-W0-02 | W0 | 0 | TAG-01, TAG-02 | unit | `cd client && npx vitest run src/test/chips.test.ts` | ❌ W0 | ⬜ pending |
| 03-W0-03 | W0 | 0 | TAG-04, TAG-05 | route | `cd server && npx vitest run tests/routes/tags.test.ts` | ❌ W0 | ⬜ pending |
| 03-W0-04 | W0 | 0 | SRCH-01, SRCH-02 | route | `cd server && npx vitest run tests/routes/search.test.ts` | ❌ W0 | ⬜ pending |
| 03-W0-05 | W0 | 0 | BM-01, BM-02 | route | `cd server && npx vitest run tests/routes/bookmarks.test.ts` | ❌ W0 | ⬜ pending |
| 03-W0-06 | W0 | 0 | BM-01 (schema) | migration | `cd server && npx drizzle-kit push` | ❌ W0 | ⬜ pending |
| 03-BULL-09 | mdrender | 1 | BULL-09 | unit | `cd client && npx vitest run src/test/markdown.test.ts` | ❌ W0 | ⬜ pending |
| 03-BULL-10 | mdrender | 1 | BULL-10 | unit | `cd client && npx vitest run src/test/bulletContent.test.ts` | ❌ W0 | ⬜ pending |
| 03-TAG-01 | chips | 2 | TAG-01 | unit | `cd client && npx vitest run src/test/chips.test.ts` | ❌ W0 | ⬜ pending |
| 03-TAG-02 | chips | 2 | TAG-02 | unit | `cd client && npx vitest run src/test/chips.test.ts` | ❌ W0 | ⬜ pending |
| 03-TAG-03 | chips | 2 | TAG-03 | manual | manual — browser: type !!, verify date picker opens | n/a | ⬜ pending |
| 03-TAG-04 | tagbrowser | 3 | TAG-04 | route | `cd server && npx vitest run tests/routes/tags.test.ts` | ❌ W0 | ⬜ pending |
| 03-TAG-05 | tagbrowser | 3 | TAG-05 | route | `cd server && npx vitest run tests/routes/tags.test.ts` | ❌ W0 | ⬜ pending |
| 03-SRCH-01 | search | 4 | SRCH-01 | route | `cd server && npx vitest run tests/routes/search.test.ts` | ❌ W0 | ⬜ pending |
| 03-SRCH-02 | search | 4 | SRCH-02 | route | `cd server && npx vitest run tests/routes/search.test.ts` | ❌ W0 | ⬜ pending |
| 03-SRCH-03 | search | 4 | SRCH-03 | manual | manual — click result, verify zoom navigation | n/a | ⬜ pending |
| 03-SRCH-04 | search | 4 | SRCH-04 | manual | manual — Ctrl+F opens modal, toolbar icon works | n/a | ⬜ pending |
| 03-BM-01 | bookmarks | 5 | BM-01 | route | `cd server && npx vitest run tests/routes/bookmarks.test.ts` | ❌ W0 | ⬜ pending |
| 03-BM-02 | bookmarks | 5 | BM-02 | route | `cd server && npx vitest run tests/routes/bookmarks.test.ts` | ❌ W0 | ⬜ pending |
| 03-BM-03 | bookmarks | 5 | BM-03 | manual | manual — click bookmark, verify zoom navigation | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `client/src/test/markdown.test.ts` — failing stubs for BULL-09 (`renderBulletMarkdown`)
- [ ] `client/src/test/chips.test.ts` — failing stubs for TAG-01, TAG-02 (`renderWithChips`)
- [ ] `client/src/test/bulletContent.test.ts` — failing stubs for BULL-10 (view/edit mode toggle)
- [ ] `server/tests/routes/tags.test.ts` — failing stubs for TAG-04, TAG-05
- [ ] `server/tests/routes/search.test.ts` — failing stubs for SRCH-01, SRCH-02
- [ ] `server/tests/routes/bookmarks.test.ts` — failing stubs for BM-01, BM-02
- [ ] `server/db/migrations/0001_bookmarks.sql` — bookmarks table migration (prerequisite for bookmark tests)
- [ ] Schema update: `bookmarks` table added to `server/db/schema.ts`
- [ ] Client deps: `cd client && npm install marked dompurify @types/dompurify`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| !! opens date picker | TAG-03 | Browser date input interaction | Type `!!` in a bullet, verify native date picker opens; select date, verify chip inserted as `!![YYYY-MM-DD]` |
| Click result opens bullet | SRCH-03 | Browser navigation + zoom state | Search for text, click result, verify URL changes and bullet is zoomed; verify cross-doc navigation |
| Ctrl+F opens modal | SRCH-04 | Keyboard + DOM visual check | Press Ctrl+F, verify modal appears centered; toolbar search icon also opens it |
| Click bookmark zooms | BM-03 | Browser navigation + zoom state | In bookmarks screen, click a row, verify URL changes to zoomed view of that bullet |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
