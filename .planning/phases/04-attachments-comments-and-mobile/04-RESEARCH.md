# Phase 4: Attachments, Comments, and Mobile - Research

**Researched:** 2026-03-09
**Domain:** File uploads (multer), PDF rendering (PDF.js), touch gestures (Pointer Events), visualViewport keyboard toolbar, inline notes
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Attachment display:**
- Each attachment is a stacked row below the bullet content (not a gallery strip): `└ 🖼️  photo.jpg  [x]`
- Images: small inline thumbnail (~80px tall) in the row
- Clicking an image → lightbox overlay (centered, full-resolution, Esc or close button to dismiss, stays in app)
- PDFs: render first page as thumbnail via PDF.js / canvas API; clicking opens PDF in new tab
- Other file types: download icon + filename; clicking downloads the file
- Multiple attachments per bullet: each is its own row, stacked vertically below bullet text
- Attachments persist below the bullet even when not focused (always visible if present)

**Focus toolbar (desktop + mobile — same actions):**
- The focus toolbar replaces DocumentToolbar when a bullet has focus (on both desktop and mobile)
- DocumentToolbar (Search, Bookmarks, Hide completed, Delete completed) shows in no-focus state
- When a bullet is focused: DocumentToolbar disappears, focus toolbar takes its place
- Focus toolbar actions: `indent | outdent | ↑ | ↓ | undo | redo | 📎 attachment | 💬 note | 🔖 bookmark | ✓ complete | 🗑 delete`
- On mobile: focus toolbar is positioned fixed above the keyboard using `visualViewport` API
- Note icon in focus toolbar: filled/highlighted when note exists; clicking creates note if none and focuses it; clicking when note exists focuses the note for editing
- Attachment icon in focus toolbar: opens file picker for that bullet

**Comments / Notes (Dynalist-style inline notes):**
- Not a side panel — notes appear as a single inline row directly below the bullet text, in smaller/dimmer font
- One note per bullet (not a thread/list of multiple comments)
- Note is always visible when it has content; hidden when empty; clearing text removes the note
- Editing: inline contenteditable — click the note text or press the note icon in focus toolbar to focus it
- Esc or clicking elsewhere saves and dismisses editing
- Deleting a note: clear the text content (empty note = no note)
- Note indicator: the note row is visible below the bullet whenever non-empty; no separate icon needed

**Mobile swipe gestures:**
- Swipe right → mark complete: green background reveals behind bullet row with ✅ icon; fires on release past ~40% row width; snaps back if released before threshold
- Swipe left → soft delete: red background reveals with 🗑️ icon; fires on release past ~40% row width; undo toast appears (~4 seconds)
- Undo toast hooks into existing soft-delete/undo infrastructure
- Drag-and-drop on mobile: same dot-drag as desktop (Phase 2 decision) — no separate drag handle

**Mobile toolbar (no-focus state):**
- DocumentToolbar shows at the top on mobile when no bullet is focused
- When a bullet is tapped and keyboard opens, focus toolbar appears fixed above keyboard (DocumentToolbar hidden)
- `visualViewport` API used to position focus toolbar correctly as keyboard resizes viewport

**NOTE: CMT-02 in REQUIREMENTS.md describes "right-edge slide-in side panel" — user has explicitly overridden this. The correct behavior is inline notes below the bullet (Dynalist style).**

### Claude's Discretion
- Exact visual styling of attachment rows (icon set, spacing, delete button style)
- Lightbox implementation (custom or lightweight library — no large deps preferred)
- Exact PDF.js integration approach (CDN vs npm, worker handling)
- Note row visual styling (font size, color, indentation relative to bullet text)
- Swipe gesture implementation approach (Pointer Events API vs touch events vs a library)
- Focus toolbar animation when replacing DocumentToolbar (instant swap or brief fade)
- Exact `visualViewport` listener implementation details

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within Phase 4 scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| BULL-16 | Mobile: long press bullet → context menu (same actions as desktop right-click) | Long-press via `touchstart`/`touchend` timer; `onContextMenu` does NOT fire on iOS Safari — must use custom implementation |
| ATT-01 | User can attach a file to a bullet (any file type, 100MB max) | multer `diskStorage` + `limits.fileSize` = 100MB; Docker volume `/data/attachments` already in `docker-compose.yml` |
| ATT-02 | Upload accessible via focus toolbar button or context menu | Focus toolbar attachment icon triggers hidden `<input type="file">`; ContextMenu adds "Attach file" item |
| ATT-03 | Images render as inline previews within the bullet | `<img src="/api/attachments/:id/file">` with `Authorization` header via `apiClient.download()` or signed URL; thumbnail ~80px tall |
| ATT-04 | PDFs show a thumbnail preview | `pdfjs-dist` npm package; render page 1 to canvas; worker via CDN matching package version |
| ATT-05 | Other file types show a download icon + filename | Blob download via `apiClient.download()` existing method; create Object URL + `<a download>` |
| ATT-06 | Files stored on Docker volume mount | Docker volume `attachments:/data/attachments` already in `docker-compose.yml`; multer `destination: '/data/attachments'` |
| CMT-01 | Bullets with comments show a small note icon indicator | Note row itself is the indicator — visible below bullet when non-empty (per locked decision) |
| CMT-02 | Note icon opens inline note (NOT side panel per locked decision) | `note` nullable text column on `bullets` table; PATCH existing endpoint; contenteditable note row in BulletNode |
| CMT-03 | User can add a plain-text comment to any bullet | `PATCH /api/bullets/:id` with `{ note: string }` — extends existing route |
| CMT-04 | User can delete their own comments | Clear note text → PATCH with `{ note: '' }` which server stores as null |
| MOB-01 | Swipe right on bullet = mark complete | Pointer Events API: `onPointerDown/Move/Up` on bullet row; track translateX; threshold 40% row width |
| MOB-02 | Swipe left on bullet = soft delete (undo available) | Same pointer event approach; existing `useSoftDeleteBullet` + undo toast component |
| MOB-03 | Long press = context menu | `touchstart` starts 500ms timer; `touchmove`/`touchend` cancel timer; fires `setContextMenuPos` at touch coordinates |
| MOB-04 | Touch-friendly drag handles for bullet reordering | dnd-kit's existing `PointerSensor` already handles touch; dot already has `touchAction: 'none'` |
| MOB-05 | Focus toolbar above keyboard when bullet selected | `visualViewport.addEventListener('resize')` + `scroll`; compute `bottom = window.innerHeight - visualViewport.offsetTop - visualViewport.height`; set toolbar `bottom` CSS |
</phase_requirements>

---

## Summary

Phase 4 adds three feature areas to the existing React + Express + Drizzle/PostgreSQL stack: file attachments stored on a Docker volume, Dynalist-style inline notes stored as a `note` column on the `bullets` table, and mobile touch interactions. Each area slots cleanly into existing patterns — multer for uploads (same stack), React Query mutations for notes via the existing PATCH route, and Pointer Events API for swipe/long-press gestures.

The most technically nuanced areas are PDF thumbnail generation (requires pdfjs-dist worker setup), attachment serving (binary files need Authorization header forwarding), and the `visualViewport` keyboard toolbar (iOS Safari has known quirks with `offsetTop` not resetting after keyboard dismissal — needs defensive implementation). The swipe gesture implementation requires careful `touchAction: 'none'` scoping to not break scroll and drag-and-drop.

The notes feature is simpler than CMT-01..04 suggest: a nullable `note` text column on the existing `bullets` table, surfaced via the existing PATCH endpoint. No new table needed. The focus toolbar is a significant but self-contained UX component that replaces `DocumentToolbar` when `focusedBulletId` in Zustand `uiStore` is non-null.

**Primary recommendation:** Build in order: (1) DB migration + note column + PATCH extension, (2) attachment upload/serve routes + multer, (3) BulletNode inline UI (note row + attachment rows), (4) FocusToolbar component + uiStore wiring, (5) mobile swipe gestures, (6) long-press context menu, (7) visualViewport keyboard toolbar positioning.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| multer | ^1.4.5-lts.1 | Multipart file upload middleware for Express | Official Express ecosystem middleware; handles `diskStorage`, `limits`, `fileFilter` |
| pdfjs-dist | ^5.x (latest) | Render PDF pages to canvas in browser | Mozilla's official PDF.js distribution; only production-grade option |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @types/multer | ^1.4.x | TypeScript types for multer | Always — server is TypeScript |
| pdfjs-dist worker (CDN) | matches installed version | Off-main-thread PDF parsing | Required; worker URL must exactly match package version |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| multer disk storage | multer memory storage | Memory storage doesn't survive pod restarts; disk is correct for Docker volume |
| pdfjs-dist npm | PDF.js CDN script tag | npm gives TypeScript types and version pinning; CDN requires HTML script tag; npm preferred |
| Custom swipe (Pointer Events) | react-swipeable library | react-swipeable is ~2KB and well-maintained; custom Pointer Events code is viable given project's minimal-deps preference |
| Custom long-press hook | use-long-press library | Custom is ~20 lines with timer; no extra dep needed |
| Custom lightbox | yet-another-lightbox | Custom overlay is ~40 lines for single-image case; CONTEXT.md says "no large deps preferred" → custom |

### Installation
```bash
# Server
cd server && npm install multer @types/multer

# Client
cd client && npm install pdfjs-dist
```

---

## Architecture Patterns

### Recommended Project Structure
```
server/src/
├── routes/
│   └── attachments.ts        # new: upload, list, delete, serve file
├── services/
│   └── attachmentService.ts  # new: CRUD + file path management
db/
├── schema.ts                 # add: note column to bullets; attachments table
└── migrations/               # new: 0006_phase4_attachments_notes.sql

client/src/
├── components/DocumentView/
│   ├── BulletNode.tsx         # modify: add NoteRow + AttachmentRows below BulletContent
│   ├── FocusToolbar.tsx       # new: replaces DocumentToolbar when bullet has focus
│   ├── NoteRow.tsx            # new: inline contenteditable note per bullet
│   ├── AttachmentRow.tsx      # new: single attachment row (image/pdf/other)
│   ├── Lightbox.tsx           # new: full-res image overlay
│   ├── UndoToast.tsx          # new: "Bullet deleted [Undo]" toast
│   └── DocumentView.tsx       # modify: FocusToolbar + visualViewport listener
├── hooks/
│   ├── useAttachments.ts      # new: React Query for attachment CRUD
│   └── useBullets.ts          # modify: add note field to Bullet type + usePatchNote
└── store/
    └── uiStore.ts             # modify: add focusedBulletId: string | null
```

### Pattern 1: Attachment Upload via FormData
**What:** POST with `multipart/form-data` bypasses `apiClient.post()` (which sets `Content-Type: application/json`). Must call `fetch` directly or add a `postForm` method.
**When to use:** ATT-01, ATT-02

```typescript
// Source: multer official docs + apiClient existing pattern
// Add to ApiClient in client/src/api/client.ts:
async upload<T>(path: string, formData: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  if (this.accessToken) {
    headers['Authorization'] = `Bearer ${this.accessToken}`;
  }
  // Do NOT set Content-Type — browser sets it with boundary for multipart
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers,
    body: formData,
    credentials: 'include',
  });
  if (!res.ok) {
    const error = await res.json().catch(() => ({ error: 'Request failed' }));
    throw Object.assign(new Error(error.error ?? 'Request failed'), { status: res.status });
  }
  return res.json() as Promise<T>;
}
```

### Pattern 2: Multer Server Setup
**What:** DiskStorage with UUID filenames on `/data/attachments`, 100MB limit, all file types accepted.
**When to use:** ATT-06, route implementation

```typescript
// Source: https://expressjs.com/en/resources/middleware/multer.html
import multer from 'multer';
import { randomUUID } from 'node:crypto';
import path from 'node:path';

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, '/data/attachments'),
  filename: (_req, file, cb) => {
    const ext = path.extname(file.originalname);
    cb(null, `${randomUUID()}${ext}`);
  },
});

export const upload = multer({
  storage,
  limits: { fileSize: 100 * 1024 * 1024 }, // 100MB
});

// Route: POST /api/attachments/bullets/:bulletId
attachmentsRouter.post('/bullets/:bulletId', upload.single('file'), async (req, res) => {
  // req.file.filename, req.file.originalname, req.file.mimetype, req.file.size
});
```

### Pattern 3: PDF.js Thumbnail (Client)
**What:** Render PDF page 1 to canvas at reduced scale for thumbnail display.
**When to use:** ATT-04

```typescript
// Source: https://mozilla.github.io/pdf.js/examples/
import * as pdfjsLib from 'pdfjs-dist';

// Worker must match package version exactly
pdfjsLib.GlobalWorkerOptions.workerSrc =
  `https://unpkg.com/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`;

async function renderPdfThumbnail(
  pdfBlob: Blob,
  canvas: HTMLCanvasElement
): Promise<void> {
  const arrayBuffer = await pdfBlob.arrayBuffer();
  const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
  const page = await pdf.getPage(1);
  const viewport = page.getViewport({ scale: 0.3 }); // small thumbnail
  canvas.width = Math.floor(viewport.width);
  canvas.height = Math.floor(viewport.height);
  await page.render({
    canvasContext: canvas.getContext('2d')!,
    viewport,
  }).promise;
  pdf.destroy();
}
```

### Pattern 4: Serving Attachment Files with Auth
**What:** Binary file endpoint that verifies JWT before streaming the file.
**When to use:** ATT-03, ATT-05

```typescript
// Source: existing apiClient.download() pattern
// Server route: GET /api/attachments/:id/file
attachmentsRouter.get('/:id/file', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  const attachment = await getAttachment(user.id, req.params.id);
  if (!attachment) return res.status(404).json({ error: 'Not found' });
  res.setHeader('Content-Type', attachment.mimeType);
  res.setHeader('Content-Disposition', `inline; filename="${attachment.filename}"`);
  res.sendFile(attachment.storagePath, { root: '/' });
});

// Client: use apiClient.download() + createObjectURL for image/PDF thumbnails
// or direct <img src="/api/attachments/:id/file"> won't work (no auth header)
// Solution: fetch blob and set as object URL
```

### Pattern 5: Note Column on Bullets Table
**What:** Add nullable `note text` column to the `bullets` table. Reuse existing `PATCH /api/bullets/:id` endpoint.
**When to use:** CMT-01, CMT-02, CMT-03, CMT-04

```typescript
// Source: schema.ts pattern (Phase 1 locked — Drizzle column addition)
// In db/schema.ts, add to bullets table:
note: text('note'),   // nullable, no default

// In bulletService.ts patchBullet — note is already passed through via the patch body
// In the route, extend the patch schema:
const patchBulletSchema = z.object({
  content: z.string().optional(),
  isComplete: z.boolean().optional(),
  isCollapsed: z.boolean().optional(),
  note: z.string().nullable().optional(),  // add this
});
// When note is empty string '', store as null: note === '' ? null : note
```

### Pattern 6: FocusToolbar via uiStore focusedBulletId
**What:** Zustand uiStore gains `focusedBulletId: string | null`. BulletContent sets it on focus/blur. DocumentView/DocumentToolbar reads it to decide which toolbar to show.
**When to use:** MOB-05, focus toolbar on all platforms

```typescript
// Source: uiStore.ts existing pattern
// Add to UiStore type:
focusedBulletId: string | null;
setFocusedBulletId: (id: string | null) => void;

// BulletContent: on focus → setFocusedBulletId(bullet.id); on blur → setFocusedBulletId(null)
// DocumentView: const { focusedBulletId } = useUiStore();
// Render: focusedBulletId ? <FocusToolbar bulletId={focusedBulletId} /> : <DocumentToolbar ... />
// NOTE: focusedBulletId must NOT be persisted (partialize excludes it — same as sidebarTab)
```

### Pattern 7: visualViewport Keyboard Toolbar
**What:** Position FocusToolbar as `position: fixed` above the keyboard on mobile using `visualViewport.offsetTop + visualViewport.height`.
**When to use:** MOB-05

```typescript
// Source: https://dev.to/franciscomoretti/fix-mobile-keyboard-overlap-with-visualviewport-3a4a
// Inside FocusToolbar (or a hook useMobileKeyboardOffset):
useEffect(() => {
  const vv = window.visualViewport;
  if (!vv) return;

  function update() {
    // bottom of fixed toolbar = distance from viewport bottom to window bottom
    const offsetFromBottom = window.innerHeight - vv!.offsetTop - vv!.height;
    setKeyboardOffset(Math.max(0, offsetFromBottom));
  }

  vv.addEventListener('resize', update);
  vv.addEventListener('scroll', update);
  update();
  return () => {
    vv.removeEventListener('resize', update);
    vv.removeEventListener('scroll', update);
  };
}, []);

// Toolbar style:
// position: 'fixed', bottom: keyboardOffset, left: 0, right: 0
```

### Pattern 8: Swipe Gesture (Pointer Events)
**What:** Per-bullet-row Pointer Events to track horizontal swipe, reveal colored background, fire action at 40% threshold.
**When to use:** MOB-01, MOB-02

```typescript
// Source: MDN Pointer Events + established mobile pattern
// On the BulletNode outer div (not the dot):
const rowRef = useRef<HTMLDivElement>(null);
const [swipeX, setSwipeX] = useState(0);
const startX = useRef(0);
const isPointerDown = useRef(false);

// Only activate swipe on touch (pointerType === 'touch')
// Set touch-action: 'pan-y' on the row to allow vertical scroll while detecting horizontal swipe
// touchAction on the DOT stays 'none' (for drag-and-drop)

function onPointerDown(e: React.PointerEvent) {
  if (e.pointerType !== 'touch') return;
  startX.current = e.clientX;
  isPointerDown.current = true;
  rowRef.current?.setPointerCapture(e.pointerId);
}

function onPointerMove(e: React.PointerEvent) {
  if (!isPointerDown.current) return;
  const dx = e.clientX - startX.current;
  // Only horizontal movement past 10px activates swipe; suppress if mostly vertical
  setSwipeX(dx);
}

function onPointerUp(e: React.PointerEvent) {
  if (!isPointerDown.current) return;
  isPointerDown.current = false;
  const rowWidth = rowRef.current?.offsetWidth ?? 300;
  const threshold = rowWidth * 0.4;
  if (swipeX > threshold) {
    markComplete.mutate(...);
  } else if (swipeX < -threshold) {
    softDelete.mutate(...); // then show UndoToast
  }
  setSwipeX(0);
}
```

### Pattern 9: Long Press for Context Menu (Mobile)
**What:** Custom timer-based long press — `onContextMenu` does NOT fire on iOS Safari.
**When to use:** BULL-16, MOB-03

```typescript
// Source: known iOS Safari limitation + timer-based workaround
const longPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
const touchStartPos = useRef<{ x: number; y: number } | null>(null);

function handleTouchStart(e: React.TouchEvent) {
  const touch = e.touches[0];
  touchStartPos.current = { x: touch.clientX, y: touch.clientY };
  longPressTimer.current = setTimeout(() => {
    setContextMenuPos({ x: touch.clientX, y: touch.clientY });
    longPressTimer.current = null;
  }, 500);
}

function handleTouchMove(e: React.TouchEvent) {
  // Cancel long press if finger moves > 8px (user is scrolling)
  const touch = e.touches[0];
  const pos = touchStartPos.current;
  if (pos) {
    const dx = touch.clientX - pos.x;
    const dy = touch.clientY - pos.y;
    if (Math.sqrt(dx * dx + dy * dy) > 8) {
      if (longPressTimer.current) {
        clearTimeout(longPressTimer.current);
        longPressTimer.current = null;
      }
    }
  }
}

function handleTouchEnd() {
  if (longPressTimer.current) {
    clearTimeout(longPressTimer.current);
    longPressTimer.current = null;
  }
  touchStartPos.current = null;
}
```

### Anti-Patterns to Avoid
- **Setting `Content-Type: application/json` for FormData uploads:** Breaks multipart boundary; omit Content-Type and let browser set it.
- **Storing empty string as note:** Store `null` in DB when note is cleared; check `note !== ''` before PATCH.
- **PDF.js worker version mismatch:** Worker CDN URL must use `pdfjsLib.version` dynamically, not a hardcoded version string.
- **Persisting focusedBulletId in Zustand:** Must exclude from `partialize` — it's transient UI state, page reload should not restore a focused bullet.
- **Using `onContextMenu` for mobile long press:** iOS Safari does not fire `contextmenu` events on long press — use `touchstart`/timer instead.
- **Setting `touch-action: none` on entire bullet rows:** Blocks vertical scroll. Set `touch-action: pan-y` on the row for swipe; keep `touch-action: none` only on the drag dot.
- **Serving attachment files without auth check:** Route must call `requireAuth` and verify `userId` matches attachment owner.
- **Relying on visualViewport.offsetTop resetting reliably:** iOS Safari may not reset after keyboard dismissal; always compute from current values, never cache.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Multipart file upload parsing | Custom busboy/stream handling | multer | Edge cases in boundary parsing, file size limiting, cleanup on error |
| PDF page rendering | Direct PDF binary parsing | pdfjs-dist | PDF is an extraordinarily complex format; PDF.js is the only viable browser option |
| File type validation by magic bytes | Custom file header inspection | multer `fileFilter` + mime-type check on `req.file.mimetype` | Sufficient for this use case; server already trusts the upload |
| Swipe physics with momentum | Custom velocity/momentum tracking | Keep it simple: threshold-only, no momentum | Momentum adds 10x complexity; iOS Mail-style threshold snap is the right UX |
| Keyboard height detection | Custom resize observer heuristics | `window.visualViewport` | Only reliable cross-device mechanism for keyboard height |

**Key insight:** PDF rendering is the only genuine "don't hand-roll" here — the rest (swipe, long press, lightbox, note row) is all DOM event handling that is straightforward to implement custom and avoids new dependencies.

---

## Common Pitfalls

### Pitfall 1: Attachment Image Src Requires Auth Header
**What goes wrong:** `<img src="/api/attachments/:id/file">` silently returns 401 or redirects to login; image shows as broken.
**Why it happens:** The browser's `<img>` tag does not send `Authorization: Bearer ...` headers — only cookies (covered by `credentials: 'include'`). If the route requires JWT in header, the `<img>` tag can't access it.
**How to avoid:** Fetch the file as a blob using `apiClient.download()`, create an Object URL with `URL.createObjectURL()`, set it as the image `src`. Revoke with `URL.revokeObjectURL()` on cleanup.
**Warning signs:** Attachment thumbnails show as broken images in dev tools with 401 response.

### Pitfall 2: PDF.js Worker Version Mismatch
**What goes wrong:** "Setting up fake worker" console warning or complete PDF rendering failure.
**Why it happens:** pdfjs-dist version and the worker script served from CDN must exactly match. Any mismatch causes silent fallback to fake worker or failure.
**How to avoid:** Always use `pdfjsLib.version` in the worker URL: `workerSrc = \`https://unpkg.com/pdfjs-dist@\${pdfjsLib.version}/build/pdf.worker.min.mjs\``
**Warning signs:** Console warning "Setting up fake worker" or PDF thumbnails never render.

### Pitfall 3: Swipe Interfering with Drag-and-Drop
**What goes wrong:** Horizontal swipe gesture activates on the drag dot, preventing dnd-kit from initiating drag.
**Why it happens:** dnd-kit's `PointerSensor` and the swipe listener both respond to `pointerdown` on overlapping elements.
**How to avoid:** Swipe listeners go on the row container div, NOT the dot. The dot keeps `touchAction: 'none'` for dnd-kit. The row uses `touchAction: 'pan-y'` to allow vertical scroll and only capture horizontal movement. Add an `if (e.pointerType !== 'touch') return;` guard to prevent swipe from activating on desktop mouse.
**Warning signs:** Drag-and-drop stops working on mobile, or swipe fires when trying to drag.

### Pitfall 4: Long Press Fires Alongside Tap/Click
**What goes wrong:** Context menu appears AND a bullet focus/click action fires at the same time.
**Why it happens:** `touchend` fires after a long press completes, which browsers convert to a synthetic `click` event.
**How to avoid:** In `handleTouchEnd`, if the context menu was triggered (timer fired), call `e.preventDefault()` to suppress the synthetic click. Set a flag `longPressTriggered.current = true` in the timer callback; check and reset in `handleTouchEnd`.
**Warning signs:** Context menu flashes and disappears immediately because a click outside closes it.

### Pitfall 5: visualViewport offsetTop Not Resetting
**What goes wrong:** After keyboard dismissal, the focus toolbar stays elevated above the bottom of screen.
**Why it happens:** iOS Safari 17/18 known bug — `visualViewport.offsetTop` does not always reset to 0 when keyboard closes, especially when user scrolls just after.
**How to avoid:** Always recompute `bottom = window.innerHeight - vv.offsetTop - vv.height`. When the keyboard closes, `vv.height` approaches `window.innerHeight` and `vv.offsetTop` approaches 0, so `offsetFromBottom` approaches 0 naturally. Additionally, listen to both `resize` AND `scroll` events and always use current values.
**Warning signs:** Focus toolbar appears floating mid-screen after keyboard dismissal.

### Pitfall 6: Multer File Not Cleaned Up on Error
**What goes wrong:** If the request fails after multer writes the file but before the DB record is created, the file persists on disk forever.
**Why it happens:** Multer writes to disk synchronously in the middleware; if the route handler throws after, the DB insert never happens.
**How to avoid:** Wrap route handler in try/catch; on error, call `fs.unlink(req.file.path)` to delete the orphaned file before returning 500.
**Warning signs:** `/data/attachments` fills up with orphaned files over time.

### Pitfall 7: Note Contenteditable vs Bullet Contenteditable Focus Conflicts
**What goes wrong:** Pressing Escape in the note field tries to dismiss both the note and trigger bullet blur/undo-checkpoint.
**Why it happens:** Event bubbling — `keydown` on the note field bubbles up to BulletNode and then to global keyboard handler.
**How to avoid:** In the note contenteditable's `onKeyDown` handler, call `e.stopPropagation()` to prevent Escape from bubbling to the bullet's keyboard handler. Note Escape = save note and blur note field only (not the bullet).
**Warning signs:** Pressing Escape in a note unexpectedly navigates or triggers undo.

---

## Code Examples

### Attachment Table Schema (Drizzle)
```typescript
// Source: db/schema.ts existing pattern
export const attachments = pgTable('attachments', {
  id: uuid('id').primaryKey().defaultRandom(),
  bulletId: uuid('bullet_id').notNull().references(() => bullets.id, { onDelete: 'cascade' }),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  filename: text('filename').notNull(),        // original filename
  mimeType: text('mime_type').notNull(),
  size: bigint('size', { mode: 'number' }).notNull(),  // bytes
  storagePath: text('storage_path').notNull(), // absolute path on disk
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  index('attachments_bullet_id_idx').on(t.bulletId),
  index('attachments_user_id_idx').on(t.userId),
]);
```

### Note Column Migration
```typescript
// Source: db/schema.ts bullets table — add one nullable column
// In schema.ts bullets table definition, add:
note: text('note'),   // null = no note; empty string stored as null by service layer

// Migration SQL (drizzle-kit generates this):
ALTER TABLE bullets ADD COLUMN note text;
```

### useAttachments Hook Pattern
```typescript
// Source: useBookmarks.ts existing pattern
export function useBulletAttachments(bulletId: string) {
  return useQuery<Attachment[]>({
    queryKey: ['attachments', bulletId],
    queryFn: () => apiClient.get<Attachment[]>(`/api/attachments/bullets/${bulletId}`),
    enabled: !!bulletId,
  });
}

export function useUploadAttachment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ bulletId, file }: { bulletId: string; file: File }) => {
      const fd = new FormData();
      fd.append('file', file);
      return apiClient.upload<Attachment>(`/api/attachments/bullets/${bulletId}`, fd);
    },
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: ['attachments', vars.bulletId] }),
  });
}
```

### UndoToast Component Pattern
```typescript
// Source: existing useSoftDeleteBullet + useUndo pattern
// Triggered by swipe-left delete; shows for 4 seconds
export function UndoToast({ bulletId, documentId, onDismiss }: Props) {
  const undo = useUndo();
  useEffect(() => {
    const t = setTimeout(onDismiss, 4000);
    return () => clearTimeout(t);
  }, [onDismiss]);

  return (
    <div style={{ position: 'fixed', bottom: 80, left: '50%', transform: 'translateX(-50%)',
                  background: '#333', color: 'white', padding: '8px 16px', borderRadius: 4,
                  display: 'flex', gap: 12, alignItems: 'center', zIndex: 2000 }}>
      Bullet deleted
      <button onClick={() => { undo.mutate(); onDismiss(); }}
              style={{ color: '#4A90E2', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600 }}>
        Undo
      </button>
    </div>
  );
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `touch-action: none` globally for gestures | Scoped `touch-action: pan-y` on swipe rows, `none` only on drag dot | Best practice | Preserves native scroll while enabling swipe |
| `position: fixed; bottom: 0` for mobile toolbar | `visualViewport`-computed `bottom` | iOS 13+ | Only reliable way to stay above keyboard |
| `onContextMenu` for right-click+long-press | `onContextMenu` desktop + `touchstart` timer for mobile | iOS 13+ regression | iOS never fires contextmenu on long press |
| PDF.js version 2.x CommonJS import | pdfjs-dist 5.x ESM import with `.min.mjs` worker | 2024 | Worker suffix changed `.js` → `.mjs` for ESM packages |
| `blob:` Object URLs for images (request per render) | Object URLs with `useEffect` cleanup (`URL.revokeObjectURL`) | Always correct | Memory leak if not revoked |

**Deprecated/outdated:**
- `pdf.worker.min.js`: In pdfjs-dist 5.x the worker is `pdf.worker.min.mjs` (ESM). Verify actual filename after install.
- `navigator.vibrate()` for long-press feedback: Avoid — inconsistent support, not needed for this use case.

---

## Open Questions

1. **Attachment image serving: Object URL vs inline auth cookie**
   - What we know: `<img src>` does not send Authorization header; `apiClient.download()` fetches blob; Object URL works.
   - What's unclear: If the app ever gains cookie-based auth (vs JWT in memory), serving via `<img src>` would work. Currently it cannot.
   - Recommendation: Use Object URL approach. Add `getObjectUrl(attachmentId)` helper in `useAttachments`. Each `AttachmentRow` fetches blob on mount and revokes on unmount.

2. **PDF.js worker ESM vs CJS filename in pdfjs-dist 5.x**
   - What we know: pdfjs-dist 5.x uses ESM. Worker file was `pdf.worker.min.js` in v3/v4, likely `pdf.worker.min.mjs` in v5.
   - What's unclear: Exact filename until the package is installed and inspected.
   - Recommendation: After `npm install pdfjs-dist`, check `node_modules/pdfjs-dist/build/` for actual worker filename. Use `pdfjsLib.version` in the CDN URL regardless.

3. **iOS 17/18 visualViewport specific regressions**
   - What we know: STATE.md flags this as a known concern. General `visualViewport` `offsetTop` bug exists. iOS 26 (future) also has known fixed positioning issues per search results.
   - What's unclear: Exact iOS 17 vs 18 behavior differences at time of implementation.
   - Recommendation: Implement the defensive `offsetFromBottom` calculation. Test on real device during the deploy checkpoint plan. Fallback: if `window.visualViewport` is undefined, position toolbar at `bottom: 0` with padding.

4. **Swipe conflict with dnd-kit on mobile**
   - What we know: dnd-kit uses `PointerSensor` which handles touch. Swipe listener also uses pointer events. The dot has `touchAction: 'none'`.
   - What's unclear: Whether horizontal pointer movement on the row (not dot) could trigger dnd-kit's drag.
   - Recommendation: Use dnd-kit's `activationConstraint: { distance: 8 }` (already configured in Phase 2) — a short horizontal swipe won't reach the drag activation distance without touching the dot. However, set the row's swipe listener to only activate when `pointerType === 'touch'` and horizontal delta exceeds vertical delta by 2x (directional lock).

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | vitest 4.x (client) + vitest 3.x (server) |
| Config file | `client/vite.config.ts` (test section) + `server/vitest.config.ts` |
| Quick run command (client) | `cd client && npx vitest run --reporter=verbose` |
| Quick run command (server) | `ssh root@192.168.1.50 "cd /root/notes/server && .venv/bin/pytest --tb=short -q"` or `cd server && npx vitest run` |
| Full suite command | Both client + server vitest run |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BULL-16 | Long press triggers context menu (mobile) | unit (logic) | `cd client && npx vitest run src/test/longPress.test.ts -x` | ❌ Wave 0 |
| ATT-01 | Upload attachment, get back record with id/filename/mimeType | unit (service mock) | `cd server && npx vitest run tests/attachments.test.ts -x` | ❌ Wave 0 |
| ATT-02 | Upload via file picker inserts attachment row | unit (hook mock) | `cd client && npx vitest run src/test/useAttachments.test.ts -x` | ❌ Wave 0 |
| ATT-03 | Image attachment row renders img with object URL | unit (component) | `cd client && npx vitest run src/test/attachmentRow.test.tsx -x` | ❌ Wave 0 |
| ATT-04 | PDF attachment triggers pdfjs thumbnail render | unit (logic) | `cd client && npx vitest run src/test/pdfThumbnail.test.ts -x` | ❌ Wave 0 |
| ATT-05 | Non-image/PDF attachment shows filename + download trigger | unit (component) | `cd client && npx vitest run src/test/attachmentRow.test.tsx -x` | ❌ Wave 0 |
| ATT-06 | multer writes file to /data/attachments path | unit (service mock) | `cd server && npx vitest run tests/attachments.test.ts::disk_storage -x` | ❌ Wave 0 |
| CMT-01 | Bullet with non-empty note shows NoteRow | unit (component) | `cd client && npx vitest run src/test/noteRow.test.tsx -x` | ❌ Wave 0 |
| CMT-02 | Clicking note icon in toolbar focuses NoteRow | unit (logic) | `cd client && npx vitest run src/test/noteRow.test.tsx -x` | ❌ Wave 0 |
| CMT-03 | PATCH /api/bullets/:id with note field saves note | unit (service mock) | `cd server && npx vitest run tests/bullets.test.ts::patch_note -x` | ❌ Wave 0 (extend existing) |
| CMT-04 | Clearing note text PATCHes with note=null | unit (logic) | `cd client && npx vitest run src/test/noteRow.test.tsx::clear_note -x` | ❌ Wave 0 |
| MOB-01 | Swipe right past threshold calls markComplete | unit (logic) | `cd client && npx vitest run src/test/swipeGesture.test.ts -x` | ❌ Wave 0 |
| MOB-02 | Swipe left past threshold calls softDelete | unit (logic) | `cd client && npx vitest run src/test/swipeGesture.test.ts -x` | ❌ Wave 0 |
| MOB-03 | Long press 500ms fires context menu; touchmove cancels | unit (logic) | `cd client && npx vitest run src/test/longPress.test.ts -x` | ❌ Wave 0 |
| MOB-04 | Touch drag via dot reorders bullets | manual | Manual test on mobile device | N/A — dnd-kit PointerSensor already handles this |
| MOB-05 | visualViewport keyboard offset positions toolbar | unit (logic) | `cd client && npx vitest run src/test/keyboardOffset.test.ts -x` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** Run relevant test file only (e.g., `npx vitest run src/test/swipeGesture.test.ts`)
- **Per wave merge:** `cd client && npx vitest run` + `cd server && npx vitest run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `server/tests/attachments.test.ts` — covers ATT-01, ATT-02, ATT-06 (service-level: upload, list, delete, disk path)
- [ ] `client/src/test/attachmentRow.test.tsx` — covers ATT-03, ATT-05 (image/other rendering logic)
- [ ] `client/src/test/pdfThumbnail.test.ts` — covers ATT-04 (pdfjs integration stub — mock pdfjsLib)
- [ ] `client/src/test/noteRow.test.tsx` — covers CMT-01, CMT-02, CMT-04 (note display, focus, clear)
- [ ] `client/src/test/swipeGesture.test.ts` — covers MOB-01, MOB-02 (threshold logic, no DOM gestures needed — pure function test)
- [ ] `client/src/test/longPress.test.ts` — covers BULL-16, MOB-03 (timer logic — pure function)
- [ ] `client/src/test/keyboardOffset.test.ts` — covers MOB-05 (visualViewport offset calculation — pure function)
- [ ] `server/tests/bullets.test.ts` — extend existing file to cover CMT-03 (note patch)
- [ ] Framework install: none needed — vitest already installed on both client and server

---

## Sources

### Primary (HIGH confidence)
- [expressjs/multer official docs](https://expressjs.com/en/resources/middleware/multer.html) — DiskStorage, limits, fileFilter API
- [PDF.js examples (mozilla.github.io)](https://mozilla.github.io/pdf.js/examples/) — getDocument, getPage, getViewport, render API
- Existing codebase: `docker-compose.yml` — `attachments:/data/attachments` volume already defined
- Existing codebase: `db/schema.ts` — current table structure, Drizzle patterns confirmed
- Existing codebase: `client/src/api/client.ts` — `download()` method exists, JSON-only `post()` confirmed
- Existing codebase: `client/src/store/uiStore.ts` — partialize pattern for transient vs persisted state

### Secondary (MEDIUM confidence)
- [Fix mobile keyboard overlap with VisualViewport (dev.to)](https://dev.to/franciscomoretti/fix-mobile-keyboard-overlap-with-visualviewport-3a4a) — `dvh` CSS preferred; `visualViewport` JS as fallback; verified against MDN
- [pdfjs-dist npm page](https://www.npmjs.com/package/pdfjs-dist) — latest version is 5.x; worker naming convention
- Multiple sources confirm: `onContextMenu` does not fire on iOS Safari 13+ on long press — requires `touchstart` timer workaround

### Tertiary (LOW confidence)
- [WebSearch: iOS 17/18 visualViewport offsetTop not resetting] — forum posts confirm bug exists; exact iOS version behavior at implementation time needs real-device testing
- [WebSearch: pdfjs-dist 5.x worker filename `.mjs`] — inferred from ESM pattern; must verify after install

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — multer and pdfjs-dist are the unambiguous choices; verified via official docs
- Architecture: HIGH — patterns follow directly from existing codebase conventions
- Pitfalls: HIGH — most are verified from official bug reports or codebase analysis; iOS visualViewport is MEDIUM (real-device behavior may vary)
- Test infrastructure: HIGH — existing vitest setup confirmed; test file paths follow established convention

**Research date:** 2026-03-09
**Valid until:** 2026-04-09 (stable stack; pdfjs-dist releases frequently but API is stable)
