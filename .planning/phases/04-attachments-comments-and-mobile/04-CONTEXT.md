# Phase 4: Attachments, Comments, and Mobile - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

File attachments per bullet (upload, inline display, lightbox for images, PDF thumbnail preview, download for other types), Dynalist-style inline notes per bullet (single note, always visible when non-empty, inline contenteditable editing), and mobile touch interactions (swipe gestures, long press context menu, focus toolbar above keyboard on mobile + replacing DocumentToolbar on desktop when bullet is focused). No new structural outliner features — all augmenting the existing bullet tree.

</domain>

<decisions>
## Implementation Decisions

### Attachment display
- Each attachment is a stacked row below the bullet content (not a gallery strip):
  `└ 🖼️  photo.jpg  [x]`
  `└ 📄  report.pdf  [x]`
  `└ 📎  archive.zip  [x]`
- Images: small inline thumbnail (~80px tall) in the row
- Clicking an image → lightbox overlay (centered, full-resolution, Esc or close button to dismiss, stays in app)
- PDFs: render first page as thumbnail via PDF.js / canvas API; clicking opens PDF in new tab
- Other file types: download icon + filename; clicking downloads the file
- Multiple attachments per bullet: each is its own row, stacked vertically below bullet text
- Attachments persist below the bullet even when not focused (always visible if present)

### Focus toolbar (desktop + mobile — same actions)
- **The focus toolbar replaces DocumentToolbar when a bullet has focus** (on both desktop and mobile)
- DocumentToolbar (Search, Bookmarks, Hide completed, Delete completed) shows in no-focus state
- When a bullet is focused: DocumentToolbar disappears, focus toolbar takes its place
- Focus toolbar actions: `indent | outdent | ↑ | ↓ | undo | redo | 📎 attachment | 💬 note | 🔖 bookmark | ✓ complete | 🗑 delete`
- On mobile: focus toolbar is positioned fixed above the keyboard using `visualViewport` API
- Note icon in focus toolbar: filled/highlighted when note exists; clicking creates note if none and focuses it for editing; clicking when note exists focuses the note for editing
- Attachment icon in focus toolbar: opens file picker for that bullet

### Comments / Notes (Dynalist-style inline notes)
- **Not a side panel** — notes appear as a single inline row directly below the bullet text, in smaller/dimmer font (Dynalist pattern)
- One note per bullet (not a thread/list of multiple comments)
- Note is always visible when it has content; hidden when empty; clearing text removes the note
- Editing: inline contenteditable (same pattern as bullet text editing) — click the note text or press the note icon in focus toolbar to focus it for editing
- Esc or clicking elsewhere saves and dismisses editing
- Deleting a note: clear the text content (empty note = no note)
- Note indicator in outline: the note row is visible below the bullet whenever non-empty; no separate icon indicator on the bullet itself needed (the note text is the indicator)

### Mobile swipe gestures
- Swipe right → mark complete: green background reveals behind bullet row with ✅ icon as you swipe; action fires on release past ~40% row width; row snaps back if released before threshold
- Swipe left → soft delete: red background reveals with 🗑️ icon as you swipe; action fires on release past ~40% row width; undo toast appears ("Bullet deleted  [Undo]", ~4 seconds)
- Undo toast hooks into existing soft-delete/undo infrastructure — same as keyboard delete + undo
- Drag-and-drop on mobile: same dot-drag as desktop (Phase 2 decision) — hold dot briefly, drag; consistent cross-platform, no separate drag handle

### Mobile toolbar (no-focus state)
- DocumentToolbar (Search, Bookmarks, etc.) shows at the top on mobile when no bullet is focused
- When a bullet is tapped and the keyboard opens, the focus toolbar appears fixed above the keyboard (DocumentToolbar hidden)
- `visualViewport` API used to position focus toolbar correctly as keyboard resizes viewport — note iOS 17/18 behavior variation (from STATE.md blocker, verify during testing)

### Claude's Discretion
- Exact visual styling of attachment rows (icon set, spacing, delete button style)
- Lightbox implementation (custom or lightweight library — no large deps preferred)
- Exact PDF.js integration approach (CDN vs npm, worker handling)
- Note row visual styling (font size, color, indentation relative to bullet text)
- Swipe gesture implementation approach (Pointer Events API vs touch events vs a library)
- Focus toolbar animation when replacing DocumentToolbar (instant swap or brief fade)
- Exact `visualViewport` listener implementation details

</decisions>

<specifics>
## Specific Ideas

- Notes model is explicitly Dynalist-style: single note per bullet, always rendered below bullet text in smaller font when present. NOT a threaded comment system, NOT a side panel.
- The focus toolbar is a significant UX upgrade: same toolbar on desktop (replaces top bar on focus) and mobile (above keyboard). Unified mental model across platforms.
- Swipe gestures follow iOS Mail / Reminders pattern: colored background reveal, threshold-based, not full-row swipe.
- REQUIREMENTS.md CMT-02 described a "right-edge slide-in side panel" — user has explicitly overridden this. The correct behavior is inline notes below the bullet (Dynalist style).

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BulletNode.tsx`: Renders each bullet row — attachment rows and note row will be added as children inside this component, below `BulletContent`
- `BulletContent.tsx`: Per-bullet contenteditable — note editing can follow the same contenteditable pattern (separate instance for the note row)
- `ContextMenu.tsx`: Already has indent/outdent/move/complete/delete/bookmark — needs attachment (open file picker) and note (focus note field) items added
- `DocumentToolbar.tsx`: Becomes the no-focus toolbar — needs to be swappable with a new `FocusToolbar` component when a bullet has focus
- `apiClient` from `src/api/client`: `.download()` method already exists for blob responses — reuse for attachment downloads; multipart upload via `apiClient.post` with FormData
- `useBookmarks.ts` / `useBullets.ts`: React Query pattern to follow for `useAttachments` and `useNotes` hooks

### Established Patterns
- React Query for all server state (useQuery / useMutation + invalidateQueries)
- Optimistic updates pattern (onMutate apply, onError rollback, onSettled revalidate)
- Zustand `uiStore` for transient UI state — `focusedBulletId` can live here to drive toolbar switching
- Plain contenteditable per bullet (locked from Phase 2) — note field follows same approach
- Minimal Dynalist aesthetic — attachment rows and note rows should be subtle, not heavy UI

### Integration Points
- `BulletNode.tsx` → add note row + attachment rows below `BulletContent`
- `DocumentView.tsx` → add `FocusToolbar` component; wire `focusedBulletId` from uiStore to determine which toolbar to show
- `ContextMenu.tsx` → add attachment + note items
- Schema: `attachments` table needed (id, bulletId, userId, filename, mimeType, size, path, createdAt)
- Schema: `notes` table or `note` column on `bullets` table — single note per bullet suggests a nullable `note` text column on bullets is simplest (no separate table)
- Server: new routes `/api/attachments` (upload, list, delete) + note handled via `PATCH /api/bullets/:id` (existing route, add `note` field)
- File storage: Docker volume at `/data/attachments` (from PROJECT.md) — multer middleware on upload route
- `visualViewport` API: mobile focus toolbar listens to `visualViewport.resize` to reposition above keyboard

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 4 scope.

</deferred>

---

*Phase: 04-attachments-comments-and-mobile*
*Context gathered: 2026-03-09*
