# Pitfalls Research

**Domain:** Self-hosted multi-user outliner/PKM web app (Dynalist/Workflowy clone)
**Researched:** 2026-03-09
**Confidence:** HIGH (tree/DB pitfalls), MEDIUM (mobile UX, undo/redo architecture)

---

## Critical Pitfalls

### Pitfall 1: Fractional Indexing Breaks Due to PostgreSQL Collation

**What goes wrong:**
Fractional indexing generates lexicographically-ordered string keys (e.g., `aZ`, `aa`, `b`) to avoid rewriting all sibling positions on reorder. The algorithm assumes standard ASCII byte order where `aa > aZ`. PostgreSQL's default collation (glibc `en_US.UTF-8` on Linux) treats character comparison case-insensitively in certain locales, so it evaluates `aZ > aa`. When querying for the "largest current key" to generate the next one, PostgreSQL returns the wrong key. The next insert reuses an existing key, causing unique constraint violations or silent ordering corruption.

**Why it happens:**
The fractional-indexing npm library is tested against JavaScript's `String.prototype.localeCompare` or raw ASCII byte order — not against PostgreSQL collation. The bug only manifests after enough inserts to generate keys with mixed case, so it passes early testing. PayloadCMS hit this exact bug (May 2025, issue #12397).

**How to avoid:**
One of two strategies — pick one at schema design time:
1. Use `TEXT` column with `COLLATE "C"` (byte-order collation): `position TEXT COLLATE "C" NOT NULL`. This forces PostgreSQL to compare strings by raw byte value, matching JavaScript's behavior.
2. Skip fractional indexing entirely and use `NUMERIC` (or `FLOAT8`) with a midpoint strategy: `new_pos = (prev_pos + next_pos) / 2.0`. Rebalance periodically when precision runs out (values differ by < 1e-9). This avoids string collation entirely.

Recommendation: **Use `FLOAT8` midpoint positioning**. It avoids the collation trap, is easy to reason about, and rebalancing is a simple background job. Fractional indexing adds string manipulation complexity with no benefit for this single-user-per-document use case.

**Warning signs:**
- Unique constraint violations on the `position` column after early use
- Items swap positions after a reorder then refresh
- Items appear in wrong order only after enough nested items exist (> 26 deep at one level)

**Phase to address:** Phase 1 (core tree CRUD — schema design must bake this in before any data exists)

---

### Pitfall 2: Undo Invalidation After a Delete Removes Children

**What goes wrong:**
The undo stack stores the ID of a deleted bullet. When undoing a delete, the system restores the bullet — but if that bullet had children that were also deleted (cascade), restoring only the parent leaves children as orphans in `deleted` state. Undo appears to work (parent reappears) but the subtree is silently lost. Conversely, if children are NOT cascade-deleted (only soft-deleted independently), undoing the parent delete without also undoing child deletes leaves a bullet with no visible children.

**Why it happens:**
Undo commands are designed as single-step inverses. A "delete bullet" command records `bullet_id` but not the subtree. Cascade soft-delete of children is done implicitly in the database layer, invisible to the command handler. The undo handler reverses only what it recorded.

**How to avoid:**
- Undo commands must record the **full affected subtree** at the time of execution, not just the target node ID.
- When performing a delete, the command handler must explicitly enumerate all descendants (via recursive CTE or pre-loaded tree), store them in the undo record (as a JSON snapshot or list of IDs), and the undo handler must restore all of them.
- Alternative: use **event sourcing** — store the before-state snapshot of the full affected subtree as the undo payload. Larger storage, but trivially correct.
- Undo history table schema: `payload JSONB` should store `{ type, bulletId, subtreeSnapshot: [...] }`, not just `{ type, bulletId }`.

**Warning signs:**
- "Undo delete" restores a bullet with no children when it previously had children
- Children appear as orphans with `deleted_at IS NOT NULL` but `parent_id` pointing to a live bullet

**Phase to address:** Phase 1 (undo/redo system — must be designed correctly before it is used anywhere, retrofitting is very expensive)

---

### Pitfall 3: Position Conflicts on Concurrent Reorder (Race Condition)

**What goes wrong:**
Two rapid drag-and-drop moves (or two browser tabs open, or a mobile + desktop session) each read the current sibling positions and compute a new midpoint. If they both read the same sibling positions before either write completes, they compute identical new positions. The second write either violates a unique constraint or silently overwrites the first, resulting in items appearing in the wrong order.

**Why it happens:**
Position computation (`new_pos = (prev + next) / 2`) is a read-then-write operation. Without serialization, two concurrent requests perform the same read and independently compute the same value.

**How to avoid:**
- Wrap the "read siblings, compute new position, update row" in a **single database transaction with `SELECT ... FOR UPDATE`** on the affected sibling rows. This serializes concurrent moves at the database level.
- Alternative (simpler for single-server, single-user app): use an advisory lock per `(user_id, document_id)` pair for move operations. Since this app is single-user-per-document with no collaboration, this is sufficient.
- Do NOT compute positions in application code and send the final value in an API call — always compute inside a transaction.

**Warning signs:**
- Unique constraint errors on `position` column under rapid movement
- Items occasionally land in wrong position after fast consecutive reorders
- Integration tests pass but manual rapid-fire drag test fails

**Phase to address:** Phase 1 (tree CRUD API — position update endpoint must be transactional from the start)

---

### Pitfall 4: Mobile Virtual Keyboard Breaks Layout and Loses Cursor

**What goes wrong:**
On iOS Safari, when the virtual keyboard opens, the viewport does NOT resize — it instead zooms into and partially hides the document. A `position: fixed` toolbar at the bottom ends up behind the keyboard. The active bullet input scrolls out of view. On both iOS and Android, calling `.focus()` programmatically does NOT open the keyboard — it only works when triggered directly from a user interaction event. This means "create new bullet on Enter" — which programmatically focuses the new input — fails to show the keyboard on iOS.

**Why it happens:**
iOS Safari intentionally refuses to open the keyboard on programmatic `.focus()` calls not originating from a direct user input event (tap/click). This is a WebKit anti-annoyance policy, not a bug, and it has not been removed despite years of developer complaints. Additionally, iOS does not fire a viewport resize event on keyboard open, so CSS `100vh` layouts break invisibly.

**How to avoid:**
- Use `visualViewport.addEventListener('resize', ...)` (available on iOS 13+, all modern Android) to detect keyboard open/close and adjust layout accordingly.
- Never place interactive controls in `position: fixed` elements at the bottom; use `position: sticky` within the scrollable container or compensate with `visualViewport` offset.
- For Enter-to-create-new-bullet: the new bullet's focus must happen inside the same event handler that processes the Enter keypress. Do not defer with `setTimeout` or `await` — iOS drops the user-gesture flag in async continuations.
- Test bullet creation flow on a real iOS device early. Simulators do not accurately replicate keyboard behavior.

**Warning signs:**
- "New bullet on Enter" works on desktop but keyboard doesn't appear on iOS after pressing Enter
- Bottom toolbar overlaps content on mobile after keyboard opens
- `visualViewport.height < window.innerHeight` when keyboard is open (correct detection signal)

**Phase to address:** Phase 1 (core editor — the keyboard interaction is fundamental to the outliner loop and cannot be retrofitted)

---

### Pitfall 5: Drag-and-Drop Parent-into-Child Corruption

**What goes wrong:**
When dragging a bullet node and dropping it onto one of its own descendants, the node becomes its own ancestor. The adjacency list now contains a cycle: `A.parent_id = B`, `B.parent_id = A`. Any recursive CTE traversal (`WITH RECURSIVE`) will loop infinitely, locking the server process until timeout or OOM kill. The document becomes unrenderable for that user.

**Why it happens:**
Drag-and-drop drop-zone detection is computed from current DOM position. During an active drag, the subtree under the dragged item is still rendered in the DOM. If the drag library allows dropping on children (no ancestry check), this is exploitable by any user — including accidentally.

**How to avoid:**
- Before accepting any drop, server-side: run a recursive CTE that fetches all descendants of the dragged node. Reject the move if the target `parent_id` is in that set.
- Client-side: when a drag starts, compute the full set of descendant IDs and mark those drop zones as invalid. This prevents the request from ever reaching the server.
- Add a PostgreSQL constraint trigger that detects cycles using `WITH RECURSIVE` on every `parent_id` update. Expensive but acts as a last-resort safeguard.

**Warning signs:**
- Drag library does not have built-in ancestry validation
- API `PATCH /bullets/:id/move` accepts `parent_id` without server-side validation
- Infinite loop / timeout errors on tree load after certain moves

**Phase to address:** Phase 1 (drag-and-drop — both client and server validation must exist before drag is shipped)

---

### Pitfall 6: Undo History Grows Without Bound and Corrupts on Schema Change

**What goes wrong:**
The undo history table stores `payload JSONB` snapshots. Over months of use, this table accumulates tens of thousands of rows per user. More critically: if the payload format changes (a field renamed, a new required key added) during a code update, old undo records with the old schema cause runtime errors when the undo handler tries to deserialize them. Result: undo silently breaks or throws 500 errors for users with existing history.

**Why it happens:**
Undo payload schemas are treated as internal data, not versioned API contracts. When the codebase evolves, old records in the database are not migrated.

**How to avoid:**
- Add a `schema_version INTEGER` column to the undo history table from day one.
- Every undo handler checks `schema_version` before parsing; unknown versions are silently skipped (no undo available for that step).
- Run a background job (or cron) that prunes undo history to the most recent 50 records per user. This also enforces the stated 50-level limit and prevents unbounded growth.
- Never rename keys in `payload` without a migration.

**Warning signs:**
- Undo history table grows proportionally to user activity without a cleanup job
- No `schema_version` field on undo records
- Undo throws 500 after a code deploy

**Phase to address:** Phase 1 (undo/redo system — schema versioning must be added at table creation, not retrofitted)

---

### Pitfall 7: iOS Touch Gesture Conflicts with Native Scroll

**What goes wrong:**
Swipe-right (complete) and swipe-left (delete) touch gestures conflict with the browser's native vertical scroll and horizontal page-back navigation. On iOS, a horizontal swipe near the left edge triggers the browser's "back" gesture, navigating away from the app. On Android, a fast horizontal swipe may scroll the page diagonally if the browser interprets the gesture as scroll. This makes bullet swipe actions feel broken or dangerous on mobile.

**Why it happens:**
Touch event listeners default to passive mode in modern browsers (for scroll performance). `preventDefault()` inside a passive listener is ignored. Even non-passive listeners can't reliably intercept iOS's edge-swipe navigation.

**How to avoid:**
- Set `touch-action: pan-y` on each bullet row via CSS. This tells the browser: vertical scroll is allowed, but horizontal touches belong to the app. The browser will not scroll horizontally when `touch-action: pan-y` is set, allowing horizontal swipe to be handled by JS without `preventDefault()`.
- Register touch listeners as `{ passive: false }` only on elements where you need to call `preventDefault()`.
- For iOS edge-swipe conflict: ensure swipe targets are at least 20px from the screen edge, or use a long-press context menu instead of left/right swipe for destructive actions.
- Implement swipe gesture detection with a minimum distance threshold (e.g., 60px) and a maximum angle tolerance (e.g., 30 degrees from horizontal) to reduce false activations during scroll.

**Warning signs:**
- Swiping on mobile also scrolls the page
- Accidental browser back navigations when swiping left
- `touch-action` not set on bullet list items

**Phase to address:** Phase dedicated to mobile gestures (touch interactions)

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Integer positions (1, 2, 3, ...) for bullet ordering | Simple to implement | Every reorder updates O(n) sibling rows; conflicts under concurrent moves | Never — use float midpoint from the start |
| Compute tsvector at query time (no stored column) | No trigger to maintain | Full table scan on every search; unusable beyond ~500 bullets | Never for search — always use stored generated column + GIN index |
| Store undo payload without schema_version | Faster initial build | Silent undo breakage after schema changes | Never |
| Hardcode `position: fixed` bottom toolbar on mobile | Simple CSS | Keyboard overlap on iOS; layout breaks | Never — use visualViewport-aware positioning |
| Load entire bullet tree in one query for rendering | Simple code | Single large document with 5,000+ bullets causes slow initial load | Acceptable for MVP; add lazy loading at Phase: performance |
| No ancestry check on drag-and-drop drop target | Faster shipping | Cycle creation corrupts documents permanently | Never — check takes < 1ms with correct index |
| Cascade hard-delete children when parent is deleted | Simple SQL | Makes undo of parent delete impossible | Never — use soft delete throughout |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Google OAuth (google-auth-library) | Trusting `aud` claim without verifying it matches your client ID | Always verify `aud === GOOGLE_CLIENT_ID` server-side before creating/logging in user |
| Multer (file uploads) | Using relative `./uploads` path as destination | Use absolute path matching Docker volume mount: `/data/attachments` exactly |
| Multer | Not upgrading past 1.x | Multer 2.0.2 (May 2025) fixes two high-severity CVEs; use 2.x |
| Docker volume (file uploads) | App runs as `node` user (UID 1000) but volume owned by root | `RUN mkdir -p /data/attachments && chown -R node:node /data/attachments` in Dockerfile, or use `user: node` in compose |
| PostgreSQL full-text search | Calling `to_tsvector()` inside WHERE clause | Use a stored generated column `search_vector tsvector GENERATED ALWAYS AS (to_tsvector(...)) STORED` + GIN index |
| JWT auth | Long-lived tokens with no refresh mechanism | Issue short-lived access tokens (15min) + long-lived refresh tokens; store refresh in httpOnly cookie |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Loading entire document tree per request | Slow initial page load; mobile timeout | Load tree lazily; collapse state determines which nodes are fetched | ~2,000 bullets in one document |
| No GIN index on `search_vector` | Full-text search takes 2–10 seconds | Add `CREATE INDEX CONCURRENTLY ... USING GIN (search_vector)` at schema creation | ~500 bullets |
| Recursive CTE without depth limit | Infinite loop / timeout if cycle exists in tree | Add `WHERE depth < 100` guard to all recursive CTEs | On first data corruption |
| Soft-deleted rows polluting sibling position queries | Reorder calculations include deleted items, creating gaps | Always filter `WHERE deleted_at IS NULL` in position queries | After first delete operation |
| N+1 queries for rendering bullet list (one query per bullet for metadata) | Slow render, many DB round-trips | Always fetch attachments/comments counts in a single JOIN or subquery with the bullet list | ~50 bullets visible |
| No pagination on search results | Searches return thousands of rows | Limit search results to 100 per query, add offset pagination | ~1,000 total bullets |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| File upload with no MIME type validation | Malicious file executed if served directly; stored XSS via SVG | Validate both `Content-Type` header AND magic bytes; never serve uploaded files from the same origin as the app (use a separate `/files/` path with `Content-Disposition: attachment`) |
| No file size check before writing to disk | Disk exhaustion attack; Docker volume fills up | Enforce 100MB limit in Multer config (`limits: { fileSize: 100 * 1024 * 1024 }`) before the file reaches disk |
| Returning full bullet tree for any user ID | User A can read user B's documents | Every query must include `WHERE user_id = $currentUserId`; add row-level check in every route handler, not just middleware |
| Undo history accessible across users | User A can undo user B's actions (or inspect their data) | Always include `WHERE user_id = $currentUserId` in undo history queries |
| Storing Google OAuth `access_token` in database | Leaked DB exposes tokens | Only store `sub` (subject ID) from the ID token; never store access tokens |
| Open registration with no rate limiting | Bots fill server storage with attachments | Add rate limiting to registration and file upload endpoints; consider a registration invite code for self-hosted use |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Undo that silently fails (returns 200 but nothing changes) | User thinks undo is broken; loss of trust | Always return a diff of what changed from undo; show "Nothing to undo" toast when stack is empty |
| Enter key creates bullet at wrong nesting level | Most-used action feels broken | Enter always creates a sibling at the same level; Tab/Shift+Tab handles indent. Never auto-indent on Enter |
| Drag ghost image obscures drop target on mobile | User can't see where item will land | Use a slim "insertion line" indicator between items, not a full ghost duplicate |
| Inline markdown rendering eating the cursor position | Cursor jumps on every keystroke when markdown is re-parsed | Only re-render markdown decorations outside the cursor's paragraph, or use a virtual DOM diffing approach that preserves selection |
| Zoom into bullet that has no children shows empty page | Disorienting — user loses context | Always show breadcrumb even if content is empty; display placeholder "No items yet — press Enter to add one" |
| Collapse state not persisted | User collapses large subtrees to navigate; refresh resets everything | Persist collapse state server-side per user per bullet from the start; do not use localStorage as primary store |
| Swipe-to-delete with no undo | Accidental delete loses data permanently | Show a brief undo toast after swipe-delete ("Deleted — Undo" with 5s timeout) that calls the server undo endpoint |

---

## "Looks Done But Isn't" Checklist

- [ ] **Tree reorder:** Verify that positions are computed inside a database transaction with row locking, not in application code before the UPDATE call
- [ ] **Undo delete:** Create a bullet with 3 children, delete parent, undo — verify all 4 items (parent + 3 children) reappear
- [ ] **Mobile Enter key:** Test on a real iOS device — verify the keyboard stays open after pressing Enter to create a new bullet
- [ ] **Drag-and-drop cycle prevention:** Drag a bullet onto one of its grandchildren — verify the move is rejected with an error, not silently accepted
- [ ] **File upload Docker permissions:** Deploy fresh, upload a file, restart the container, verify the file is still accessible
- [ ] **Full-text search index:** Run `EXPLAIN ANALYZE` on the search query — verify it shows `Bitmap Index Scan` on the GIN index, not `Seq Scan`
- [ ] **Undo across page refresh:** Make an edit, refresh the page, press Ctrl+Z — verify undo works (server-side persistence confirmed)
- [ ] **Fractional positioning:** Create 30+ siblings at one level, reorder several, refresh — verify order is preserved exactly
- [ ] **Soft delete subtree:** Delete a bullet with children, check database — verify all descendants have `deleted_at` set (no orphans)
- [ ] **Collapse state persistence:** Collapse a subtree with 10 items, refresh — verify collapsed state is restored from server, not defaulted to expanded

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Position collation bug (string ordering wrong) | HIGH | Migrate `position` column to `FLOAT8`; recompute all positions from current display order; run migration in transaction |
| Undo subtree corruption (children not restored) | HIGH | Add subtree snapshot to undo payload; write a one-time migration script that marks corrupted undo records with schema_version=0 so they are skipped |
| Document cycle corruption (A is ancestor of itself) | HIGH | Write a repair script using `WITH RECURSIVE` with cycle detection (`CYCLE id SET is_cycle USING path`) to find and break cycles; move cycled node to document root |
| Docker volume permission failure (uploads unreadable) | MEDIUM | `docker exec` into container, `chown -R node:node /data/attachments`; update Dockerfile for future deploys |
| tsvector not indexed (slow search) | LOW | `CREATE INDEX CONCURRENTLY bullets_search_gin ON bullets USING GIN (search_vector)` — runs without locking table |
| Undo history schema mismatch after deploy | LOW | Deploy records schema_version on new rows; old rows without version are skipped; users lose old undo history but current operations work |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Fractional indexing / collation | Phase 1: Tree CRUD schema | `EXPLAIN` shows correct ORDER BY; rapid reorder smoke test |
| Undo subtree restore | Phase 1: Undo/redo system | Delete-subtree-undo integration test |
| Position race condition | Phase 1: Tree CRUD API | Concurrent move requests in test |
| Mobile keyboard focus | Phase 1: Core editor | Manual test on real iOS device |
| Drag parent-into-child cycle | Phase 1: Drag-and-drop | Attempt invalid drop; server returns 4xx |
| Undo history unbounded growth | Phase 1: Undo/redo system | Row count after 60+ operations = 50 (pruned) |
| iOS touch gesture conflicts | Mobile gestures phase | swipe-on-scroll does not trigger both actions |
| tsvector at query time (no index) | Search phase | `EXPLAIN ANALYZE` shows GIN index hit |
| Docker volume permissions | Infrastructure / file attachments phase | Fresh deploy + upload + restart cycle test |
| Inline markdown cursor jump | Editor polish phase | Type in a bold-wrapped line; cursor stays put |
| Collapse state lost on refresh | Phase 1: Tree CRUD | Collapse, refresh, verify state from server |

---

## Sources

- [The PostgreSQL Collation Trap That Breaks Fractional Indexing — Jökull Sólberg](https://www.solberg.is/fractional-indexing-gotcha) (January 2026, HIGH confidence)
- [PayloadCMS Issue #12397: Orderable field _order column uses case-sensitive fractional indexing, causing unique constraint issues in PostgreSQL](https://github.com/payloadcms/payload/issues/12397) (May 2025, HIGH confidence)
- [PostgreSQL glibc Collation and Data Corruption — Crunchy Data Blog](https://www.crunchydata.com/blog/glibc-collations-and-data-corruption) (MEDIUM confidence — general collation risk)
- [ProseMirror iOS mobile keyboard issues — GitHub Issues](https://github.com/ProseMirror/prosemirror/issues/627) (MEDIUM confidence)
- [CodeMirror iOS Safari: Missing selection drag handles](https://discuss.codemirror.net/t/ios-safari-missing-selection-drag-handles/9679) (MEDIUM confidence)
- [Building Complex Nested Drag and Drop UIs With React DnD — Kustomer Engineering](https://medium.com/kustomerengineering/building-complex-nested-drag-and-drop-user-interfaces-with-react-dnd-87ae5b72c803) (MEDIUM confidence)
- [PostgreSQL: Speeding up recursive queries and hierarchical data — CYBERTEC](https://www.cybertec-postgresql.com/en/postgresql-speeding-up-recursive-queries-and-hierarchic-data/) (HIGH confidence)
- [Cycle Detection for Recursive Search in Hierarchical Trees — SQLforDevs](https://sqlfordevs.com/cycle-detection-recursive-query) (HIGH confidence)
- [Optimizing Full Text Search with Postgres tsvector Columns and Triggers — Thoughtbot](https://thoughtbot.com/blog/optimizing-full-text-search-with-postgres-tsvector-columns-and-triggers) (HIGH confidence)
- [Handling Permissions with Docker Volumes — Deni Bertovic](https://denibertovic.com/posts/handling-permissions-with-docker-volumes/) (HIGH confidence)
- [Docker and Multer Upload Volumes ENOENT Error: Complete Guide 2026](https://copyprogramming.com/howto/upload-files-from-express-js-and-multer-to-persistent-docker-volume) (MEDIUM confidence)
- [VirtualKeyboard API — MDN Web Docs](https://developer.mozilla.org/en-US/docs/Web/API/VirtualKeyboard_API) (HIGH confidence)
- [Debugging virtual keyboard bug in mobile Safari — Medium](https://imeugenia.medium.com/debugging-is-thinking-the-story-of-virtual-keyboard-bug-in-mobile-safari-1623c878660e) (MEDIUM confidence)

---
*Pitfalls research for: Self-hosted outliner/PKM web app (Dynalist/Workflowy clone)*
*Researched: 2026-03-09*
