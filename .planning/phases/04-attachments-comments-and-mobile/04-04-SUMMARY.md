---
phase: 04-attachments-comments-and-mobile
plan: "04"
subsystem: client-ui
tags: [attachments, notes, react, tdd, pdfjs, lightbox]
dependency_graph:
  requires: ["04-02", "04-03"]
  provides: ["inline-note-ui", "attachment-display-ui", "lightbox"]
  affects: ["BulletNode", "DocumentView"]
tech_stack:
  added: ["pdfjs-dist"]
  patterns: ["contenteditable-inline-edit", "blob-object-url", "react-query-mutations"]
key_files:
  created:
    - client/src/hooks/useAttachments.ts
    - client/src/components/DocumentView/NoteRow.tsx
    - client/src/components/DocumentView/AttachmentRow.tsx
    - client/src/components/DocumentView/Lightbox.tsx
  modified:
    - client/src/hooks/useBullets.ts
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/test/noteRow.test.tsx
    - client/src/test/attachmentRow.test.tsx
    - client/src/test/pdfThumbnail.test.ts
decisions:
  - "pdfjs-dist vi.mock needed in both test files: pdfThumbnail.test.ts (primary) and attachmentRow.test.tsx (prevents DOMMatrix crash in jsdom when AttachmentRow imports pdfjs at module level)"
  - "renderPdfThumbnail exported as named export from AttachmentRow.tsx so it can be unit-tested without mounting React component"
  - "BulletNode wraps BulletContent+NoteRow+AttachmentRow in a flex-1 div to allow column layout while preserving existing dot+chevron layout"
metrics:
  duration: "~12min"
  completed_date: "2026-03-09"
  tasks: 2
  files: 9
---

# Phase 04 Plan 04: Inline Bullet Augmentation UI Summary

**One-liner:** Dynalist-style inline NoteRow + multi-type AttachmentRow (image/PDF/other) wired into BulletNode with useAttachments React Query hook.

## Tasks Completed

### Task 1: useAttachments hook + NoteRow component
- **Commit:** 7722898
- Created `useAttachments.ts` with `useBulletAttachments`, `useUploadAttachment`, `useDeleteAttachment` following the useBookmarks.ts pattern
- Added `note: string | null` to the `Bullet` type in `useBullets.ts`
- Added `usePatchNote()` mutation to `useBullets.ts` (PATCH `/api/bullets/:id` with note field)
- Created `NoteRow.tsx`: contenteditable div with `useLayoutEffect` to set initial content, `onBlur` saves if changed, `onKeyDown` Escape reverts and calls `stopPropagation()` to prevent global handler interference
- Replaced all 3 throw stubs in `noteRow.test.tsx` with 6 real passing tests

### Task 2: AttachmentRow + Lightbox + wire into BulletNode
- **Commit:** 58ebfc6
- Created `Lightbox.tsx`: 35-line fixed overlay with centered image, Esc key handler, click-outside close
- Created `AttachmentRow.tsx` with three sub-components dispatched by MIME type:
  - **Image**: fetches blob via `apiClient.download()`, creates Object URL, renders 80px thumbnail, opens `Lightbox` on click
  - **PDF**: fetches blob, calls `renderPdfThumbnail()` (pdfjs-dist at scale 0.3) to canvas element, click opens blob URL in new tab
  - **Other**: shows filename + paperclip emoji, click triggers blob download via `<a download>` element
  - Export `renderPdfThumbnail` as named export for unit testing
- Modified `BulletNode.tsx`: added `useBulletAttachments` + `useDeleteAttachment` hooks, renders `<NoteRow>` when `bullet.note !== null`, maps `<AttachmentRow>` for each attachment
- Replaced throw stubs in `attachmentRow.test.tsx` (2 tests) and `pdfThumbnail.test.ts` (1 test) with real passing tests

## Test Results

All 9 tests pass:
- `noteRow.test.tsx`: 6 tests (renders with note, null note, clearing note, changing note, no-patch on same, Escape reverts)
- `attachmentRow.test.tsx`: 2 tests (image renders Object URL, other shows filename)
- `pdfThumbnail.test.ts`: 1 test (verifies getDocument called, page 1 fetched, render called)

TypeScript: `npx tsc --noEmit` clean.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] pdfjs-dist vi.mock hoisting issue in pdfThumbnail.test.ts**
- **Found during:** Task 2 TDD RED
- **Issue:** Test used `const getDocumentMock = vi.fn()` referenced inside `vi.mock()` factory — but `vi.mock` is hoisted to top of file, causing "Cannot access before initialization" error
- **Fix:** Access the mock via the imported `pdfjsLib.getDocument` after module-level mock, cast with `as ReturnType<typeof vi.fn>`
- **Files modified:** `client/src/test/pdfThumbnail.test.ts`

**2. [Rule 2 - Missing critical functionality] pdfjs-dist DOMMatrix crash in attachmentRow test**
- **Found during:** Task 2 GREEN phase
- **Issue:** AttachmentRow.tsx imports pdfjs-dist at module level; pdfjs-dist modern build calls `DOMMatrix` on import which doesn't exist in jsdom (Node env) — crashes test before any code runs
- **Fix:** Added `vi.mock('pdfjs-dist', ...)` to `attachmentRow.test.tsx` to stub the entire module before import
- **Files modified:** `client/src/test/attachmentRow.test.tsx`

## Self-Check: PASSED

Files verified: all 5 key files exist on disk.
Commits verified: 7722898 and 58ebfc6 present in git log.
