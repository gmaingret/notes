---
phase: 04-attachments-comments-and-mobile
verified: 2026-03-09T22:10:00Z
status: gaps_found
score: 13/16 must-haves verified
gaps:
  - truth: "Note button in FocusToolbar focuses NoteRow (creates note if none exists)"
    status: failed
    reason: "FocusToolbar dispatches CustomEvent('focus-note') but no component listens for it. BulletNode only renders NoteRow when bullet.note !== null — clicking Note toolbar button does nothing when note is null. The note cannot be created via the toolbar."
    artifacts:
      - path: "client/src/components/DocumentView/FocusToolbar.tsx"
        issue: "handleNote() dispatches 'focus-note' CustomEvent (line 139) but no addEventListener exists anywhere in the codebase"
      - path: "client/src/components/DocumentView/BulletNode.tsx"
        issue: "NoteRow rendered only when bullet.note !== null (line 270-271). No event listener for 'focus-note' to show/create note."
    missing:
      - "BulletNode needs a useEffect that listens for document CustomEvent('focus-note', ...) filtering by bulletId, sets noteVisible state to true, and passes focusOnMount=true to NoteRow"
      - "BulletNode needs to render NoteRow when noteVisible===true OR bullet.note!==null (not just when note exists)"

  - truth: "'Add note' in ContextMenu focuses NoteRow"
    status: failed
    reason: "ContextMenu's handleAddNote() dispatches CustomEvent('focus-note') — same broken path as FocusToolbar Note button. No listener exists."
    artifacts:
      - path: "client/src/components/DocumentView/ContextMenu.tsx"
        issue: "handleAddNote() dispatches 'focus-note' event (line 129) but no listener handles it"
    missing:
      - "Same fix as above: BulletNode must listen for 'focus-note' CustomEvent"

  - truth: "'Attach file' in ContextMenu opens file picker for the bullet"
    status: failed
    reason: "ContextMenu's handleAttachFile() dispatches CustomEvent('attach-file') but no component listens for it. The FocusToolbar attach button works (has its own fileInputRef), but ContextMenu 'Attach file' does nothing."
    artifacts:
      - path: "client/src/components/DocumentView/ContextMenu.tsx"
        issue: "handleAttachFile() dispatches 'attach-file' CustomEvent (line 124) but no listener exists"
      - path: "client/src/components/DocumentView/BulletNode.tsx"
        issue: "No addEventListener for 'attach-file' event; no hidden file input wired to this event"
    missing:
      - "BulletNode needs a hidden <input type='file'> with a useEffect listener for document CustomEvent('attach-file', ...) filtering by bulletId that triggers .click() on the input"
      - "The upload mutation call on file selection must mirror FocusToolbar's handleFileChange pattern"

human_verification:
  - test: "Note button creates note on bullet with no existing note"
    expected: "Clicking Note button (or 'Add note' in ContextMenu) on a bullet with no note shows an inline NoteRow below the bullet, focused for editing"
    why_human: "CustomEvent dispatch flow requires actual DOM event propagation and component re-render — not verifiable via static analysis after gap is fixed"
  - test: "Mobile swipe gestures work on real device"
    expected: "Swipe right marks bullet complete (green reveal), swipe left soft-deletes (red reveal + UndoToast)"
    why_human: "Touch gesture behavior on iOS Safari cannot be verified programmatically"
  - test: "FocusToolbar appears above iOS keyboard"
    expected: "Tapping bullet on iPhone opens keyboard; FocusToolbar appears immediately above the keyboard, not hidden beneath it"
    why_human: "visualViewport behavior is runtime-only on iOS Safari; cannot verify statically"
  - test: "Long-press opens context menu on iOS Safari"
    expected: "Holding finger on bullet for ~500ms without moving opens ContextMenu at press coordinates"
    why_human: "Touch event behavior differs between iOS Safari and desktop — requires device testing"
  - test: "PDF thumbnail renders in AttachmentRow"
    expected: "Uploading a PDF file to a bullet shows a canvas thumbnail of page 1"
    why_human: "pdfjs-dist canvas rendering requires browser environment with real PDFs"
---

# Phase 4: Attachments, Comments, and Mobile — Verification Report

**Phase Goal:** Users can attach files and leave comments on bullets, and the full feature set works on mobile browsers via swipe and long-press gestures
**Verified:** 2026-03-09T22:10:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | Users can upload a file to a bullet via FocusToolbar Attach button | VERIFIED | FocusToolbar has hidden fileInputRef + useUploadAttachment mutation wired to handleFileChange |
| 2 | Uploaded files are stored in /data/attachments on Docker volume | VERIFIED | multer diskStorage configured to `/data/attachments`; migration 0002 creates attachments table |
| 3 | Image attachments render as ~80px thumbnail in BulletNode | VERIFIED | ImageAttachmentRow fetches blob via apiClient.download(), creates Object URL, renders `<img height=80>` |
| 4 | Image thumbnails open a fullscreen Lightbox on click | VERIFIED | Lightbox.tsx exists; ImageAttachmentRow sets lightboxOpen state on img onClick |
| 5 | PDF attachments show canvas thumbnail via pdfjs-dist | VERIFIED | PdfAttachmentRow uses renderPdfThumbnail() with pdfjsLib.getDocument; pdfThumbnail.test passes |
| 6 | Other file types show filename + download on click | VERIFIED | OtherAttachmentRow renders filename with download via `<a download>` element |
| 7 | Note appears inline below bullet when bullet.note is non-null | VERIFIED | BulletNode renders `<NoteRow>` when `bullet.note !== null`; NoteRow is contenteditable |
| 8 | Note saves on blur via PATCH; Escape reverts | VERIFIED | NoteRow.handleBlur calls patchNote.mutate; handleKeyDown Escape reverts and stops propagation |
| 9 | Clearing note text PATCHes with note=null | VERIFIED | NoteRow onBlur: `patchNote.mutate({ id: bulletId, note: current || null })` |
| 10 | Note button in FocusToolbar focuses NoteRow (creates if none) | FAILED | focus-note CustomEvent dispatched but no listener exists; NoteRow not shown for null notes |
| 11 | Add note in ContextMenu focuses NoteRow | FAILED | Same broken CustomEvent path — ContextMenu dispatches but BulletNode has no listener |
| 12 | Attach file in ContextMenu opens file picker | FAILED | attach-file CustomEvent dispatched but no listener; only FocusToolbar attach button works |
| 13 | Swipe right past 40% marks bullet complete | VERIFIED | BulletNode handleRowPointerUp calls swipeThresholdReached + markComplete.mutate |
| 14 | Swipe left past 40% soft-deletes with UndoToast | VERIFIED | BulletNode handles 'delete' result from swipeThresholdReached; UndoToast renders on showUndoToast |
| 15 | Long-press 500ms opens context menu | VERIFIED | createLongPressHandler wired via BulletNode onTouchStart/Move/End; setContextMenuPos called |
| 16 | FocusToolbar positions above keyboard via visualViewport | VERIFIED | FocusToolbar useEffect listens for vv resize/scroll, calls computeKeyboardOffset, sets bottom style |

**Score: 13/16 truths verified**

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `server/db/schema.ts` | VERIFIED | `note: text('note')` on bullets table; attachments table exported with all FK constraints |
| `server/db/migrations/0002_phase4_attachments_notes.sql` | VERIFIED | ALTER TABLE + CREATE TABLE + 2 indexes |
| `server/db/migrations/meta/_journal.json` | VERIFIED | All 3 entries (0000, 0001, 0002) present |
| `server/src/services/attachmentService.ts` | VERIFIED | 4 exports: createAttachment, getAttachmentsByBullet, getAttachment, deleteAttachment |
| `server/src/routes/attachments.ts` | VERIFIED | 4 routes: POST /bullets/:bulletId, GET /bullets/:bulletId, GET /:id/file, DELETE /:id; 100MB limit; orphan cleanup |
| `server/src/index.ts` | VERIFIED | `app.use('/api/attachments', attachmentsRouter)` at line 30 |
| `client/src/api/client.ts` | VERIFIED | `upload<T>(path, formData)` method exists; omits Content-Type for multipart |
| `client/src/hooks/useAttachments.ts` | VERIFIED | Exports Attachment type, useBulletAttachments, useUploadAttachment, useDeleteAttachment |
| `client/src/hooks/useBullets.ts` | VERIFIED | Bullet type has `note: string | null`; usePatchNote exported |
| `client/src/components/DocumentView/NoteRow.tsx` | VERIFIED | 61 lines; contenteditable; onBlur saves; Escape reverts + stopPropagation |
| `client/src/components/DocumentView/AttachmentRow.tsx` | VERIFIED | 157 lines; dispatches by MIME type (image/pdf/other); renderPdfThumbnail exported |
| `client/src/components/DocumentView/Lightbox.tsx` | VERIFIED | 44 lines; fixed overlay; Esc + click-outside close |
| `client/src/components/DocumentView/gestures.ts` | VERIFIED | swipeThresholdReached + createLongPressHandler pure exports |
| `client/src/components/DocumentView/UndoToast.tsx` | VERIFIED | 4s auto-dismiss; Undo button calls /api/undo + invalidates bullets |
| `client/src/components/DocumentView/FocusToolbar.tsx` | VERIFIED | 11 action buttons; visualViewport keyboard offset; computeKeyboardOffset exported |
| `client/src/components/DocumentView/BulletNode.tsx` | VERIFIED (partial) | Renders NoteRow + AttachmentRows; swipe + long-press handlers; UndoToast — missing focus-note/attach-file listeners |
| `client/src/store/uiStore.ts` | VERIFIED | focusedBulletId + setFocusedBulletId added; NOT in partialize (resets on reload) |
| `client/src/components/DocumentView/ContextMenu.tsx` | PARTIAL | Attach file + Add note items exist and dispatch events; but events have no listeners |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| server/src/routes/attachments.ts | server/src/services/attachmentService.ts | createAttachment, deleteAttachment calls | WIRED | All 4 service functions imported and called |
| server/src/index.ts | server/src/routes/attachments.ts | app.use('/api/attachments', attachmentsRouter) | WIRED | Line 30 confirmed |
| client/src/api/client.ts | upload method | FormData POST without Content-Type | WIRED | upload() method exists, omits Content-Type |
| client/src/components/DocumentView/BulletNode.tsx | NoteRow | conditional render when bullet.note !== null | WIRED | Line 270-271 confirmed |
| client/src/components/DocumentView/AttachmentRow.tsx | useDeleteAttachment | mutation wired to onDelete prop | WIRED | BulletNode passes deleteAttachment.mutate as onDelete |
| client/src/components/DocumentView/AttachmentRow.tsx | /api/attachments/:id/file | apiClient.download() -> URL.createObjectURL | WIRED | All 3 row types use apiClient.download() |
| client/src/components/DocumentView/BulletContent.tsx | client/src/store/uiStore.ts | setFocusedBulletId on focus/blur | WIRED | Lines 265 (null) and 273 (bullet.id) confirmed |
| client/src/components/DocumentView/BulletTree.tsx | FocusToolbar | focusedBulletId ? FocusToolbar : DocumentToolbar | WIRED | Lines 207-208 confirmed |
| client/src/components/DocumentView/FocusToolbar.tsx | visualViewport | resize + scroll event listeners | WIRED | useEffect with vv.addEventListener(resize/scroll) confirmed |
| client/src/components/DocumentView/FocusToolbar.tsx | computeKeyboardOffset | called inside visualViewport handler | WIRED | Line 52 confirmed |
| FocusToolbar Note button | BulletNode NoteRow focus | CustomEvent('focus-note') -> BulletNode listener | NOT WIRED | Event dispatched (FocusToolbar line 139) but no addEventListener in codebase |
| ContextMenu Add note | BulletNode NoteRow focus | CustomEvent('focus-note') -> BulletNode listener | NOT WIRED | Event dispatched (ContextMenu line 129) but no listener |
| ContextMenu Attach file | BulletNode file input | CustomEvent('attach-file') -> BulletNode listener | NOT WIRED | Event dispatched (ContextMenu line 124) but no listener |
| client/src/components/DocumentView/BulletNode.tsx | swipeThresholdReached | pointerType===touch guard + swipeX calculation | WIRED | handleRowPointerDown checks pointerType; swipeThresholdReached called in handleRowPointerUp |
| client/src/components/DocumentView/BulletNode.tsx | long-press timer 500ms | createLongPressHandler with delay=500 | WIRED | useMemo creates handler; touch handlers wired to onTouchStart/Move/End |
| client/src/components/DocumentView/BulletNode.tsx | UndoToast | showUndoToast state + render | WIRED | Line 291 renders UndoToast when showUndoToast |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| BULL-16 | 04-01, 04-06 | Long press bullet = context menu | SATISFIED | createLongPressHandler sets contextMenuPos; ContextMenu renders |
| ATT-01 | 04-01, 04-03, 04-04 | Attach file to bullet (any type, 100MB max) | SATISFIED | multer 100MB limit; FocusToolbar attach button functional |
| ATT-02 | 04-03, 04-05 | Upload via focus toolbar or context menu | PARTIAL | FocusToolbar attach button works; ContextMenu 'Attach file' dispatches unhandled event |
| ATT-03 | 04-04 | Images render as inline previews | SATISFIED | ImageAttachmentRow renders 80px thumbnail via Object URL |
| ATT-04 | 04-04 | PDFs show thumbnail preview | SATISFIED | PdfAttachmentRow renders canvas via renderPdfThumbnail (pdfjs-dist) |
| ATT-05 | 04-04 | Other files show download icon + filename | SATISFIED | OtherAttachmentRow renders filename + download link |
| ATT-06 | 04-02, 04-03 | Files stored on Docker volume mount | SATISFIED | multer destination='/data/attachments'; migration 0002 applied |
| CMT-01 | 04-04 | Bullets with notes show indicator | SATISFIED (re-scoped) | Per CONTEXT.md, note text IS the indicator; NoteRow visible below bullet when note exists |
| CMT-02 | 04-04, 04-05 | Clicking note icon opens/focuses NoteRow | PARTIAL | NoteRow renders and is editable when note exists; but Note button cannot CREATE a note (CustomEvent not handled) |
| CMT-03 | 04-02 | User can add plain-text note to bullet | PARTIAL | Note saves on blur via patchBullet; but Note button in toolbar/ContextMenu cannot create new note |
| CMT-04 | 04-04 | User can delete their note | SATISFIED | Clearing NoteRow text triggers PATCH with note=null |
| MOB-01 | 04-06 | Swipe right = mark complete | SATISFIED | BulletNode swipe right past 40% calls markComplete |
| MOB-02 | 04-06 | Swipe left = soft delete with undo | SATISFIED | BulletNode swipe left past 40% calls softDelete + shows UndoToast |
| MOB-03 | 04-06 | Long press = context menu | SATISFIED | createLongPressHandler 500ms delay; BulletNode touch handlers wired |
| MOB-04 | 04-07 | Touch-friendly drag handles | SATISFIED | dnd-kit PointerSensor + touchAction:none on dot; confirmed in BulletNode line 250 |
| MOB-05 | 04-05, 04-07 | Focus toolbar above keyboard | SATISFIED | FocusToolbar uses computeKeyboardOffset with visualViewport |

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `client/src/components/DocumentView/ContextMenu.tsx` L124 | Dispatches 'attach-file' CustomEvent with no listener | Blocker | "Attach file" menu item does nothing |
| `client/src/components/DocumentView/ContextMenu.tsx` L129 | Dispatches 'focus-note' CustomEvent with no listener | Blocker | "Add note" menu item does nothing |
| `client/src/components/DocumentView/FocusToolbar.tsx` L139 | Dispatches 'focus-note' CustomEvent with no listener | Blocker | Note toolbar button does nothing for bullets without existing note |
| `client/src/components/DocumentView/BulletNode.tsx` L270 | NoteRow only rendered when bullet.note !== null | Warning | Cannot create a note via toolbar/menu — only edit existing notes |

### Human Verification Required

#### 1. Mobile swipe gestures

**Test:** On an iPhone or Android phone, visit https://notes.gregorymaingret.fr, open a document, and slowly swipe right on a bullet row.
**Expected:** Green background with checkmark reveals behind the row; release past ~40% width marks bullet complete.
**Why human:** Touch Pointer Events API behavior on mobile browsers cannot be tested statically.

#### 2. Mobile long-press context menu

**Test:** Hold finger on a bullet for ~0.5 seconds without moving.
**Expected:** Context menu appears at the press location with all actions including Attach file and Add note.
**Why human:** iOS Safari onContextMenu does not fire; the timer-based touch handler must be verified on a real device.

#### 3. FocusToolbar keyboard positioning on iOS

**Test:** Tap a bullet on an iPhone. Observe where the FocusToolbar appears after the keyboard opens.
**Expected:** FocusToolbar appears immediately above the keyboard, not hidden below it or at the bottom of the screen.
**Why human:** visualViewport behavior varies between iOS 16/17/18 — only verifiable on real devices.

#### 4. PDF thumbnail rendering

**Test:** Attach a PDF file to a bullet (via FocusToolbar attach button). After upload, observe the attachment row.
**Expected:** A canvas thumbnail showing the first page of the PDF appears in the attachment row.
**Why human:** pdfjs-dist canvas rendering requires actual browser + PDF file.

#### 5. Image Lightbox

**Test:** Attach an image file to a bullet, then click the thumbnail.
**Expected:** Full-resolution image displays in a darkened overlay. Pressing Esc or clicking outside the image closes the overlay.
**Why human:** Lightbox rendering and keyboard event handling require browser environment.

### Gaps Summary

Three closely related gaps exist in the CustomEvent wiring between the FocusToolbar/ContextMenu and BulletNode:

**Root cause:** The `focus-note` and `attach-file` CustomEvents are dispatched correctly from both FocusToolbar and ContextMenu, but no component in the tree registers event listeners for them. The plan intended BulletNode to listen for these events (mentioned in 04-05 PLAN task 2 description: "dispatch a CustomEvent('focus-note') on document; BulletNode for that bullet listens and focuses its NoteRow"), but the implementation of BulletNode never added these listeners.

**Affected requirements:**
- ATT-02 (partial): ContextMenu "Attach file" non-functional; FocusToolbar attach button works
- CMT-02 (partial): Note button cannot create a new note; can only focus an already-existing NoteRow
- CMT-03 (partial): Notes can only be created by directly clicking an existing NoteRow or by some other path that first creates the note field

**Fix scope:** BulletNode needs a `useEffect` adding two document event listeners:
1. `focus-note`: set `noteVisible` state to `true` for matching bulletId; pass `focusOnMount` to NoteRow
2. `attach-file`: programmatically click a `fileInputRef` maintained within BulletNode

The NoteRow render condition must also change from `bullet.note !== null` to `bullet.note !== null || noteVisible`.

---

_Verified: 2026-03-09T22:10:00Z_
_Verifier: Claude (gsd-verifier)_
