# Feature Research

**Domain:** Native Android outliner — Kotlin/Jetpack Compose + Material Design 3 (v2.0 milestone)
**Researched:** 2026-03-12
**Confidence:** HIGH (Material 3 gesture patterns, Compose APIs, existing backend capabilities), MEDIUM (competitor Android UX parity, physical keyboard conventions)

---

## Scope Note

This file covers the **v2.0 native Android client**. The backend API is complete and unchanged. The question is: what does a native Android outliner app need to feel first-class, and what should be deferred or skipped entirely?

The web client (v1.0 + v1.1) is the canonical feature reference. This research focuses on what "table stakes" means specifically for a **native Android Material Design 3** context — where user expectations, gesture primitives, and interaction vocabulary differ from the browser.

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features Android users assume exist. Missing these = app feels like a port, not a native app.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Email/password login with JWT persistence | No session on every cold open is a showstopper | LOW | EncryptedSharedPreferences for token; httpOnly cookie for refresh handled by OkHttp CookieJar |
| Document list in ModalNavigationDrawer | Native Android navigation pattern; Dynalist and Workflowy both use drawer | MEDIUM | DrawerState; swipe-from-edge to open; close on item select |
| Bullet tree rendered as flat LazyColumn | Infinite scroll, no layout jank — LazyColumn is the only viable approach for deep trees | HIGH | Flatten tree to display list at ViewModel layer; indentation via paddingStart per depth level |
| Tap bullet to edit inline | Users expect in-place text editing, not a separate edit screen | MEDIUM | BasicTextField per bullet row; FocusRequester to bring up keyboard |
| Enter to create next bullet at same level | Universal outliner behavior; missing = app is unusable | MEDIUM | KeyboardActions with ImeAction; intercept onKeyEvent for physical keyboards |
| Tab / Shift+Tab to indent / outdent | All outliners do this; finger-reachable via toolbar buttons when soft keyboard is shown | MEDIUM | Hardware keyboard: intercept Tab via onKeyPreviewKeyEvent; touch: toolbar buttons |
| Collapse/expand bullets with children | Core outliner interaction; Workflowy/Dynalist both have it | MEDIUM | Chevron icon on bullet row; collapsed state drives tree flattening in ViewModel |
| Zoom into bullet as root (drill down) | Core outliner interaction; missing = users can't focus on subtrees | MEDIUM | Navigate to new screen or push new composition with that bullet as virtual root + breadcrumbs |
| Breadcrumb trail when zoomed in | Users need to know where they are and navigate up | LOW | Horizontal scrollable row above the list; each crumb tappable |
| Swipe right = complete bullet | Direct parity with web client; Workflowy Android uses same gesture | MEDIUM | SwipeToDismissBox with StartToEnd direction; green backing with checkmark icon |
| Swipe left = delete bullet | Direct parity with web client; standard Android destructive swipe pattern | MEDIUM | SwipeToDismissBox with EndToStart direction; red backing with trash icon; confirmation for non-empty bullets |
| Long press bullet = context menu | Standard Android pattern for contextual actions on list items | MEDIUM | DropdownMenu or ModalBottomSheet anchored to item; actions: indent, outdent, zoom, complete, delete, add comment |
| Pull to refresh | Universal Android pattern for sync; users expect it on any list screen | LOW | PullToRefreshBox (Material 3 component); re-fetches current document |
| Dark mode follows system | Android users set system dark mode and expect apps to follow | LOW | MaterialTheme dynamicColorScheme + isSystemInDarkTheme(); no manual toggle needed in v2.0 |
| Material 3 theme throughout | App feels foreign if it uses custom colors/shapes out of nowhere | MEDIUM | Dynamic color (Material You) where available; static seed color fallback for Android 11 and below |
| Back gesture (predictive back) | Android 13+ users swipe from edge to go back; app must handle this | MEDIUM | opt into predictive back in manifest; NavHost handles cross-screen; BackHandler for within-screen (e.g., close drawer) |
| Search across documents | Users can't browse 50+ docs without search | MEDIUM | SearchBar/SearchView Material 3 pattern; reuse backend /search endpoint |
| Undo last action | Server-side undo already exists; Android users expect Ctrl+Z equivalent | LOW | Undo button in top app bar; POST /api/undo — purely a UI affordance over existing API |

### Differentiators (Competitive Advantage)

Features that make the Android app feel genuinely native and polished, beyond what a mobile browser delivers.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Drag-to-reorder bullets with long-press handle | Native drag feel that a PWA cannot match; Dynalist Android does this | HIGH | Use Calvin-LL/Reorderable library (supports LazyColumn + animateItem); drag handle icon on each row; long-press to initiate; reorder API call on drop |
| Drag-to-reorder documents in drawer | Document organization without tapping through menus | MEDIUM | Same reorderable pattern inside ModalNavigationDrawer's content list |
| Optimistic updates on all mutations | Native apps feel faster than web apps; mutations should feel instant | HIGH | Update local state in ViewModel before API call; revert on error; all bullet operations (create, edit, complete, delete, indent, reorder) must be optimistic |
| Swipe animation with proportional icon reveal | Proportional color + icon as you drag; matches web v1.1 behavior and Todoist/Things pattern | MEDIUM | SwipeToDismissBox progress property + lerp() for color; icon fades in at 30% threshold |
| Physical keyboard support (Bluetooth/Chromebook) | Power users on Pixels, tablets, Chromebooks use Bluetooth keyboards | MEDIUM | Tab=indent, Shift+Tab=outdent, Enter=new bullet, Backspace-on-empty=delete+merge, Ctrl+Z=undo — all via onPreviewKeyEvent |
| Inline markdown rendering per bullet | Bold, italic, strikethrough, inline code visible without entering edit mode | HIGH | AnnotatedString built from markdown parser; switch to raw text when bullet has focus |
| #tags, @mentions, !!dates as tappable chips | Already in backend; render as colored spans; tap to filter | MEDIUM | SpanStyle with clickable modifier; tap launches search filtered by tag/mention/date |
| Comment count badge on bullets | Signals hidden context without navigating into comments | LOW | Small badge composable on bullet row; driven by comment_count field in bullet API response |
| Bookmarks screen | Users bookmark important bullets and need to access them natively | LOW | Dedicated screen accessible from drawer; reuse GET /api/bookmarks endpoint |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Offline mode with local SQLite cache | "I want it to work without internet" | Sync conflicts with server-side undo/redo model; conflict resolution on reconnect requires a full CRDT layer; out of scope per PROJECT.md | Accept online-only; make API calls fast with optimistic updates so latency is invisible |
| Rich text editor (ProseMirror/Quill equivalent) | Users want formatting toolbar like Google Docs | A document-model editor conflicts with the tree structure; the web client deliberately avoided this — same reasoning applies | AnnotatedString-based inline markdown with a minimal formatting toolbar (bold/italic/code buttons above keyboard) |
| Nested RecyclerView / nested LazyColumn | "Natural" for a tree structure | Deeply nested scrollable containers cause touch event conflicts and performance issues at scale; the web client solved this with a flat SortableContext — same pattern applies | Single flat LazyColumn with depth-based paddingStart for indentation |
| Real-time collaborative sync | "Sync between my phone and laptop simultaneously" | Server is privacy-first, single-user; real-time sync requires WebSocket or SSE infrastructure not in the backend; out of scope | Pull-to-refresh for explicit sync; optimistic updates handle the latency gap |
| Bottom navigation bar | Thumb-friendly for multiple top-level sections | This app has one primary surface (the document); a bottom nav implies multiple co-equal sections that don't exist; Dynalist/Workflowy both use drawer-only | ModalNavigationDrawer with swipe-from-left; single-surface design |
| Separate "edit mode" per bullet | Perceived safety before committing edits | Adds a tap to start every edit and a tap to confirm every edit — 2x the taps for the most frequent action in an outliner | Tap-to-edit in place; IME Done/Enter commits; Back dismisses keyboard |
| File attachment picker within bullet | Web client has this | Android SAF (Storage Access Framework) integration is non-trivial; file upload over mobile data is slow and lossy; adds significant complexity for a rarely-used feature | Defer attachments to v2.1; focus on core outlining on v2.0 |
| Google SSO on Android | Web already has it | `google-services.json` + Firebase/Auth credential flow requires Play Services dependency and adds build complexity; one-sign-in per lifetime for a self-hosted app; low ROI | Email/password auth only in v2.0; Google SSO is a v2.1 candidate |
| Kanban board view | "Organizing cards like Workflowy Boards" | Orthogonal UI paradigm; requires a completely different layout and interaction model | Outline view only; PROJECT.md is clear that the tool is an outliner, not a project manager |
| Widget / home screen shortcut to document | Convenient for power users | AppWidgetProvider implementation is substantial; requires separate layout XML + service | Deep link shortcut (android:autoVerify App Links) to a specific document is simpler and achieves 80% of the use case |

---

## Feature Dependencies

```
Auth (JWT + refresh cookie)
    └──required-by──> ALL screens (drawer, document, search, bookmarks)
    └──must-implement-first──> (nothing else works without a valid session)

ModalNavigationDrawer + Document List
    └──required-by──> Document CRUD (create/rename/delete)
    └──required-by──> Drawer document drag-reorder
    └──enhances──> Back gesture (drawer close on back)

Bullet Tree ViewModel (tree flattening)
    └──required-by──> LazyColumn rendering
    └──required-by──> Collapse/expand
    └──required-by──> Zoom/drill-down
    └──required-by──> All bullet mutations (create, edit, indent, delete, complete)
    └──required-by──> Drag-to-reorder bullets

LazyColumn with flat tree model
    └──required-by──> Swipe gestures (SwipeToDismissBox wraps each row)
    └──required-by──> Long-press context menu
    └──required-by──> Drag-to-reorder (Calvin-LL/Reorderable operates on LazyColumn)

Optimistic updates (ViewModel mutation pattern)
    └──enhances──> ALL bullet mutations (must be designed in from the start)
    └──must-NOT-be-retrofitted──> (bolt-on optimism after the fact causes state bugs)

Inline markdown rendering (AnnotatedString)
    └──required-by──> #tags, @mentions, !!dates as tappable chips
    └──conflicts-with──> Edit mode (must switch to raw text on focus)

Search
    └──required-by──> Tag/mention/date navigation (filter searches)
    └──reuses──> Existing /search backend endpoint

Physical keyboard support
    └──enhances──> All bullet editing (Tab, Shift+Tab, Enter, Backspace, Ctrl+Z)
    └──independent──> (graceful degradation when no physical keyboard present)
```

### Dependency Notes

- **Auth must be phase 1**: Every subsequent screen requires a valid JWT. Build auth + token refresh + EncryptedSharedPreferences storage before any other screen.
- **Tree flattening is the architectural foundation**: The decision to flatten the bullet tree into a display list in the ViewModel (not in the UI) determines how collapse/expand, zoom, drag, and swipe all work. This must be designed correctly in phase 1 of the outliner, not retrofitted.
- **Optimistic updates must be designed in from the start**: If the ViewModel pattern is built with pessimistic (wait-for-API) updates first and then "made optimistic" later, the state management logic doubles in complexity. Optimism is an architectural choice, not a polish step.
- **Inline markdown and edit mode are in tension**: When a bullet has focus, it must show raw text (so the user can edit `**bold**` without fighting rendering). AnnotatedString rendering is for read-only bullets only. This requires a `isFocused` flag per bullet row.
- **Drag-to-reorder requires the flat LazyColumn**: Nested lists break drag libraries. This is why the flat-list-with-depth-padding approach is non-negotiable.

---

## MVP Definition

### v2.0 Launch With

The minimum Android client that delivers the core outliner experience without the web browser overhead.

- [ ] Auth — email/password login, JWT + refresh cookie, EncryptedSharedPreferences, auto-refresh on 401
- [ ] Document management — list in drawer, create/rename/delete, last-opened persistence
- [ ] Bullet tree — flat LazyColumn, depth indentation, create/edit/delete, Tab/Shift+Tab indent, Enter new bullet, collapse/expand, zoom + breadcrumbs
- [ ] Swipe gestures — swipe right = complete, swipe left = delete, with proportional color/icon reveal
- [ ] Long-press context menu — indent, outdent, complete, delete, add comment, zoom
- [ ] Pull to refresh — explicit sync trigger on document screen
- [ ] Undo — single tap undo button in top app bar (existing server-side undo)
- [ ] Search — Material 3 SearchBar across documents and bullets
- [ ] Material 3 theme — dark/light follows system; dynamic color (Material You) on Android 12+
- [ ] Optimistic updates — all mutations update local state before API response
- [ ] Predictive back gesture — proper back handling throughout

### Add After v2.0 Launch (v2.1)

- [ ] Drag-to-reorder bullets — depends on v2.0 flat LazyColumn being stable; adds significant complexity
- [ ] Drag-to-reorder documents — same library; simpler list; bundle with bullet drag
- [ ] Inline markdown rendering — AnnotatedString; needs edit/view mode switching; polish feature
- [ ] #tags, @mentions, !!dates as tappable chips — depends on inline markdown rendering
- [ ] Comment count badge — requires comment_count in API response (minor backend addition)
- [ ] Bookmarks screen — straightforward; low complexity; good v2.1 filler
- [ ] Physical keyboard shortcuts (Bluetooth keyboards) — niche; implement after core is stable

### Future Consideration (v3+)

- [ ] Google SSO on Android — Firebase Auth dependency; not worth the build complexity for v2.0
- [ ] File attachments — SAF integration; meaningful complexity; most usage is desktop
- [ ] Widgets / App Links deep-link to document
- [ ] Import/export (Markdown) from Android — SAF-based file write; nice-to-have

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Auth (JWT + refresh) | HIGH | LOW | P1 — nothing works without it |
| Document drawer (list + CRUD) | HIGH | MEDIUM | P1 — core navigation |
| Bullet tree (flat LazyColumn + CRUD) | HIGH | HIGH | P1 — core product |
| Tab/Shift+Tab indent | HIGH | MEDIUM | P1 — outliner unusable without it |
| Collapse/expand | HIGH | MEDIUM | P1 — core outliner behavior |
| Zoom + breadcrumbs | HIGH | MEDIUM | P1 — core outliner behavior |
| Swipe gestures (complete + delete) | HIGH | MEDIUM | P1 — matches web; users expect it |
| Optimistic updates | HIGH | HIGH | P1 — must be designed in from start |
| Dark mode (system-follow) | HIGH | LOW | P1 — Android users expect this |
| Pull to refresh | MEDIUM | LOW | P1 — standard Android pattern |
| Long-press context menu | HIGH | MEDIUM | P1 — needed for indent/outdent on touch |
| Search | HIGH | MEDIUM | P1 — unusable without search at scale |
| Undo button | MEDIUM | LOW | P1 — existing API; 30 min of work |
| Predictive back | MEDIUM | MEDIUM | P1 — Android 13+ requirement |
| Drag-to-reorder bullets | MEDIUM | HIGH | P2 — powerful but complex; v2.1 |
| Inline markdown rendering | MEDIUM | HIGH | P2 — polish; v2.1 |
| #tags/@mentions/!!dates chips | MEDIUM | MEDIUM | P2 — depends on markdown rendering |
| Bookmarks screen | LOW | LOW | P2 — quick win in v2.1 |
| Physical keyboard shortcuts | LOW | MEDIUM | P2 — niche; v2.1 |
| Comment count badge | LOW | LOW | P3 — requires API change |
| File attachments | LOW | HIGH | P3 — defer to v3 |
| Google SSO | LOW | HIGH | P3 — not worth Firebase dep in v2.0 |

**Priority key:**
- P1: Must have for v2.0 launch — without these the milestone fails
- P2: Should have in v2.1 — high value but separate effort
- P3: Nice to have — future consideration

---

## Competitor Feature Analysis

| Feature | Workflowy Android | Dynalist Android | Our v2.0 Approach |
|---------|------------------|-----------------|-------------------|
| Auth | Email + Google | Email + Google | Email only (Google SSO deferred) |
| Document navigation | Side drawer | Side drawer | ModalNavigationDrawer (Material 3) |
| Bullet editing | Tap to edit inline | Tap to edit inline | BasicTextField per row, tap to edit |
| Indent/outdent | Toolbar buttons + drag | Toolbar buttons + drag | Toolbar buttons; Tab key on physical KB |
| Swipe complete | Swipe right | Swipe right | SwipeToDismissBox StartToEnd |
| Swipe delete | Swipe left (via menu) | Swipe left | SwipeToDismissBox EndToStart |
| Drag to reorder | Long-press handle | Long-press handle | v2.1 (Calvin-LL/Reorderable) |
| Search | Full-text + tags | Full-text + tags | SearchBar + existing /search API |
| Collapse/expand | Yes | Yes | Yes |
| Zoom/drill-down | Yes (tap bullet icon) | Yes | Yes (tap bullet expand icon or context menu) |
| Dark mode | System-follow | System-follow | System-follow + Material You |
| Offline | Offline-capable | Offline-capable | Online-only (deferred by design) |
| Markdown rendering | Yes (inline) | Yes (inline) | v2.1 (AnnotatedString) |
| Tags as chips | Yes | Yes | v2.1 |
| Attachments | No (mobile) | Limited | v3 |

---

## Android-Specific UX Patterns

### Bullet Editing Keyboard Flow

The standard "outliner keyboard loop" on Android with soft keyboard:

1. User taps blank area → new bullet created → keyboard opens → `ImeAction.Default` (no action button shown; Enter is the action)
2. User types text
3. User presses Enter → new sibling bullet created below; focus moves to it
4. User presses Tab (or taps indent button in floating toolbar) → current bullet becomes child of bullet above
5. User presses Shift+Tab (or taps outdent button) → current bullet promoted one level
6. User presses Backspace on empty bullet → bullet deleted; focus moves to previous bullet's end

The ImeAction must be set to `ImeAction.Default` (shows standard Enter key, not "Done" or "Search"), and `KeyboardCapitalization.Sentences` for auto-capitalize. Override `onKeyEvent` with `onPreviewKeyEvent` to intercept Tab, Shift+Tab, and Backspace-on-empty before they reach the TextField.

**Known pitfall:** Gboard and some OEM keyboards intercept Tab and do not deliver it as a KeyEvent to the app. Always provide touch-accessible toolbar buttons (indent/outdent) as the primary path; physical keyboard Tab is a secondary convenience. (Source: Obsidian Android community bug reports confirming this behavior.)

### Swipe Gesture Behavior (SwipeToDismissBox)

Per Material 3 and the existing web client pattern:

- **Swipe right (StartToEnd)**: Complete bullet. Green backing (`colorScheme.tertiary` or #10b981). Checkmark icon. `confirmValueChange` returns `false` (row stays in place after toggle — completed state is a visual change, not removal).
- **Swipe left (EndToStart)**: Delete bullet. Red backing (`colorScheme.error`). Trash icon. `confirmValueChange` returns `true` for leaf bullets (immediate removal with `animateItem()`). For bullets with children, show a ModalBottomSheet confirmation before deletion.
- **Proportional reveal**: Use `swipeToDismissBoxState.progress` + `lerp(containerColor, actionColor, progress.coerceIn(0f, 1f))` for smooth color transition. Icon fades in with `alpha = (progress - 0.25f).coerceIn(0f, 1f) / 0.25f` (appears between 25–50% swipe).

### Long-Press Context Menu

Use `DropdownMenu` anchored to the long-pressed item for in-line actions. Prefer it over ModalBottomSheet for list items because:
- DropdownMenu appears near the touch point (better spatial relationship)
- ModalBottomSheet requires bottom half of screen (disrupts reading position)
- Exception: destructive confirmation (delete subtree) → use ModalBottomSheet or AlertDialog for weight

Actions in the context menu:
1. Indent (becomes child of bullet above)
2. Outdent (promoted one level)
3. Complete / Uncomplete
4. Zoom into this bullet
5. Add comment
6. Delete (with confirmation if has children)

### Drag-to-Reorder (v2.1)

Use `sh.calvin.reorderable:reorderable` (Calvin-LL/Reorderable) — the most maintained library as of 2025, supporting `LazyColumn` with `Modifier.animateItem()`. Long-press on a drag handle icon (6-dot vertical grip icon from Material icons) initiates drag. Drop calls `PATCH /api/bullets/:id` with new position. The flat LazyColumn model (already required for rendering) is exactly what this library needs.

**Do not use** the older `aclassen/ComposeReorderable` — it has not been updated for recent Compose APIs.

### Predictive Back Handling

Register `BackHandler` in screens with sub-states to intercept before system handles it:
- Drawer open → close drawer, do not pop screen
- Keyboard open + focus in bullet → close keyboard, do not pop screen
- Zoomed into bullet → pop zoom level (navigate up), do not pop to document list

For Android 13+ predictive back animation: opt in via `android:enableOnBackInvokedCallback="true"` in AndroidManifest. NavHost in Jetpack Navigation automatically applies the cross-fade animation.

---

## Sources

- [Material 3 Gestures — m3.material.io](https://m3.material.io/foundations/interaction/gestures)
- [Material 3 Lists — m3.material.io](https://m3.material.io/components/lists/guidelines)
- [SwipeToDismissBox — developer.android.com](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss)
- [Navigation Drawer — developer.android.com](https://developer.android.com/develop/ui/compose/components/drawer)
- [Material 3 Navigation Drawer — m3.material.io](https://m3.material.io/components/navigation-drawer/guidelines)
- [Material 3 Search — m3.material.io](https://m3.material.io/components/search)
- [Search Bar — developer.android.com](https://developer.android.com/develop/ui/compose/components/search-bar)
- [Pull to Refresh Material 3 Compose — domen.lanisnik.medium.com](https://medium.com/@domen.lanisnik/pull-to-refresh-with-compose-material-3-26b37dbea966)
- [Calvin-LL/Reorderable — github.com](https://github.com/Calvin-LL/Reorderable)
- [Handle Keyboard Actions Compose — developer.android.com](https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands)
- [Predictive Back Gesture — developer.android.com](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
- [Workflowy Android — play.google.com](https://play.google.com/store/apps/details?id=com.workflowy.android)
- [Workflowy Mobile Shortcuts blog post — blog.workflowy.com](https://blog.workflowy.com/swipe-right-on-us-mobile-shortcuts/)
- [Drag Drop in Compose — dev.to/myougatheaxo](https://dev.to/myougatheaxo/drag-drop-in-compose-reorderable-lists-swipe-to-delete-4nfd)
- [Swipe Actions in Compose — dev.to/myougatheaxo](https://dev.to/myougatheaxo/swipe-actions-in-compose-swipetodismissbox-archive-delete-patterns-2i9m)
- [Obsidian Android keyboard Enter/Tab bug — forum.obsidian.md](https://forum.obsidian.md/t/enter-in-lists-on-android-gboard-doesnt-make-next-bullet-point-checkbox-etc-due-to-autosuggest-autocorrect/51716)

---

*Feature research for: Notes v2.0 Native Android Client milestone*
*Researched: 2026-03-12*
