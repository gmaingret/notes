# Deferred Items — Phase 07.1

## Pre-existing Test Failures (out of scope)

### mobileLayout.test.tsx — MOBL-01 hamburger tests (5 failures)
- **Discovered:** During Task 1 of 07.1-03
- **Root cause:** Hamburger button was moved from AppPage to DocumentView in commit `d485d97`
  (Phase 6 bug fix). The tests in mobileLayout.test.tsx still expect the button in AppPage
  and mock DocumentView to an empty div, so the hamburger is never rendered.
- **Not a Phase 7.1 regression** — these tests were already failing before any 7.1 changes.
- **Fix needed:** Update mobileLayout.test.tsx MOBL-01 tests to look for hamburger in
  DocumentView, or unmock DocumentView selectively.

### attachmentRow.test.tsx — renders img with object URL (1 timeout failure)
- **Discovered:** During Task 1 of 07.1-03
- **Root cause:** Test times out at 5000ms waiting for `waitFor` to find an `<img>` element.
  The `useEffect` in AttachmentRow that fetches the blob and creates the object URL may have
  a timing or mock issue in jsdom. This is unrelated to Phase 7.1.
- **Fix needed:** Increase test timeout or investigate why the blob fetch mock doesn't resolve
  within the default 5000ms in this test environment.
