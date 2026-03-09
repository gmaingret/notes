# Feature Research

**Domain:** Outliner / Personal Knowledge Management (PKM) web app — Dynalist/Workflowy clone
**Researched:** 2026-03-09
**Confidence:** HIGH (core features verified against Dynalist/Workflowy official docs + help centers)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume any outliner has. Missing these = product feels broken, not incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Infinite nesting of bullets | Core promise of the outliner genre — every tool has it | MEDIUM | Tree structure in DB; recursive rendering in UI |
| Enter to create new bullet at same level | Foundational editing reflex from 30+ years of outliners | LOW | Must work identically to pressing Enter in a word processor |
| Tab / Shift+Tab to indent / outdent | Universal outliner muscle memory — Dynalist, Workflowy, OmniOutliner all use it | LOW | Must preserve caret position; must work mid-sentence |
| Collapse / expand bullets with children | Users navigate large outlines by hiding detail | LOW | Collapse state must persist across page refresh |
| Drag-and-drop reordering (desktop) | Expected from all modern list UIs | HIGH | Cross-level drag is especially complex; use a battle-tested lib |
| Ctrl+Z / Ctrl+Y undo/redo | Universal editing safety net | HIGH | Server-side persistence of undo stack is a key decision point |
| Zoom into any bullet as root ("hoisting") | Workflowy invented this; it's now table stakes for the genre | MEDIUM | Replaces current view with bullet's subtree |
| Breadcrumb navigation when zoomed | Pair feature with zoom — users lose orientation without it | LOW | Shows ancestors of current zoomed root; clickable |
| Full-text search across all documents | Users rely on search as primary retrieval method | MEDIUM | Must be fast; instant-as-you-type preferred |
| Markdown formatting (bold, italic, strikethrough, inline code, links) | Expected in any modern text tool; Dynalist and Workflowy both support it | MEDIUM | WYSIWYG-style inline rendering preferred over raw markdown preview |
| Inline image rendering for image URLs / attachments | Users paste image URLs and expect them to render | LOW | Just render `<img>` tags for recognized patterns |
| Mark bullets complete / checkbox | Dynalist and Workflowy both have this; used for tasks | LOW | Visual: strikethrough + 50% opacity. Ctrl+Enter shortcut |
| Multiple documents | Workflowy's single-document limitation is widely cited as a weakness; Dynalist fixed it | LOW | Flat list (no folders) matches Workflowy/Dynalist pattern |
| Document creation, rename, delete | Basic document management | LOW | — |
| Keyboard shortcut: Ctrl+Up / Ctrl+Down to move bullet | Move bullets without drag — essential for keyboard-first users | LOW | Swap with previous/next sibling |
| Keyboard shortcut: Ctrl+] / Ctrl+[ to zoom in/out | Standard in Dynalist; expected once zoom exists | LOW | — |
| Keyboard shortcut: Ctrl+F to search within document | Universal browser/app shortcut | LOW | — |
| Keyboard shortcut: Ctrl+Enter to mark done | Standard Dynalist shortcut | LOW | — |
| Keyboard shortcut: Ctrl+B / Ctrl+I for bold/italic | Universal text formatting shortcuts | LOW | — |
| #tag syntax rendered as clickable chips | Dynalist and Workflowy both have this; used for cross-cutting organization | LOW | Clicking tag = filter view to all bullets with that tag |
| Tag browser / tag pane | Expected complement to #tags — where did I use this tag? | LOW | List of all tags with item counts |
| Bookmarks for bullets and documents | Power users bookmark frequently visited nodes | LOW | Dedicated bookmarks panel; Ctrl+Shift+B |
| Export as Markdown | Data portability is a basic expectation for any notes tool | LOW | Per-document export; flat markdown with heading levels for nesting |
| "New users start with Inbox document" | Onboarding convention; avoids empty-state confusion | LOW | Created automatically on first login |

---

### Differentiators (Competitive Advantage)

Features that distinguish this app from Dynalist/Workflowy and justify building it.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Server-side persisted undo/redo (50 levels) | Survives page refresh and browser crash — Dynalist's undo is session-only and lost on refresh | HIGH | Requires storing operation log in DB; replay on load. True differentiator vs. Dynalist. |
| Self-hosted, zero vendor lock-in | Dynalist and Workflowy are SaaS with paywalls and shutdown risk; self-hosting = data ownership | MEDIUM | Docker-based deployment; all data in user's Postgres + volume |
| No paywall on any core feature | Dynalist gates item finder, version history behind Pro ($7.99/mo) | LOW | Positioning decision; no tiers |
| File attachments per bullet | Dynalist has attachments (Pro only); Workflowy lacks them; storing files adjacent to notes is powerful | MEDIUM | Store on Docker volume; serve via signed URLs or direct paths |
| Comments per bullet (flat, plain text) | Lightweight annotation without full collaboration overhead | MEDIUM | Flat list per bullet; not threaded in v1 |
| @mention syntax rendered as chips | Extends the tag pattern for people/context; useful for personal PKM | LOW | Similar rendering to #tags; clickable filter |
| !!date syntax rendered as chips | Dates inline in bullets, not in a separate field like Dynalist's date picker | LOW | ISO-parseable format; clickable opens filtered date view |
| Mobile swipe gestures (swipe-right = complete, swipe-left = delete) | Mobile UX is an afterthought in Dynalist/Workflowy; intentional gesture design is a gap to fill | MEDIUM | Long-press for context menu; swipe with reveal animation |
| Mobile long-press context menu for indent/outdent/move | Touch users have no Tab key; context menu is the ergonomic solution | MEDIUM | Must not conflict with text selection long-press |
| Bulk delete completed bullets | Quality-of-life for task users; clearing done items is a frequent action | LOW | Finds all completed bullets in current document; confirm → delete |
| Ctrl+P / Ctrl+O quick-open for document navigation | Power users navigate by keyboard without touching the sidebar | LOW | Fuzzy-match document list; instant |

---

### Anti-Features (Commonly Requested, Often Problematic)

Features to deliberately NOT build in v1 — each one has a "why it hurts" explanation.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Real-time collaboration / shared documents | Every notes app eventually gets this request | Requires operational transform or CRDT (huge complexity), presence indicators, conflict resolution, permissions model — entire secondary product | Privacy-first positioning: collaboration is explicitly excluded; market to solo users who want that |
| Offline mode / service worker | Users want the app to work on planes | Service worker + sync = complex conflict resolution when coming back online; doubles storage (IndexedDB + Postgres) | Fast page loads; good UX on slow connections; defer to v2 |
| Bidirectional links / backlinks (Roam/Obsidian style) | Power PKM users love networked thought | Fundamentally different information architecture than a hierarchical outliner; turns every bullet into a node in a graph; implies a wiki not an outliner | Stick to #tags and @mentions for cross-referencing; avoids scope explosion |
| Graph view | Visually appealing; requested alongside backlinks | Only meaningful if bidirectional links exist; without them it's cosmetic; high implementation cost for low marginal value | — |
| Folders for document organization | Users with many documents request folders | Workflowy proved flat + search + zoom is a complete substitute for folders; folders create nesting-of-nesting confusion in an already-nested app | Tags on documents; search; a well-sorted flat list |
| Daily note / journal mode (Logseq/Roam style) | Useful for journaling workflows | Conflicts with the document-first model; creates dozens of auto-generated documents; complicates document list | Users can manually create dated documents in Inbox |
| Calendar / agenda view for dates | !!date syntax implies calendar; users extrapolate | Requires a full calendar widget; date search is sufficient for v1 | Search by date range; Dynalist's Google Calendar integration is a v2+ idea |
| Native mobile apps (iOS/Android) | Smooth mobile experience | Web-first is faster to ship; responsive web app covers 95% of use cases with zero app store overhead | Responsive web with touch gestures and PWA installability |
| Admin panel / user management | Multi-user self-hosted implies admin needs | All users equal; self-hosted admins manage via Postgres directly; admin panels are full products of their own | Document Docker Compose + Postgres setup clearly |
| Version history beyond 50-step undo | Users want "time machine" | Storing full document snapshots at every edit is expensive storage-wise; 50 steps of undo covers virtually all accident-recovery needs | 50-level server-side undo is already a strong guarantee |
| Threaded / nested comments | Comment power-users want discussion threads | Threads are a collaboration product; flat comments are sufficient for personal annotations; threads require reply-to relationships, notifications, collapsing logic | Flat comments per bullet in v1 |
| AI features (summarize, generate) | Trendy; every tool is adding AI | Adds API key management, cost, latency, privacy concerns for a self-hosted privacy-focused tool | Lean into privacy as a differentiator; users chose self-hosted to avoid AI data usage |
| Zapier / webhook integrations | Power users want automation | Integration maintenance is ongoing; webhooks require outbound HTTP + retry logic; adds surface area | Provide export as Markdown; users can automate at the file/API level |
| OPML import (from Dynalist/Workflowy) | Migration path for existing outliner users | Parsing OPML correctly preserves hierarchy but loses attachments, comments, colors; creates false expectations; becomes a support burden | Offer plain Markdown import/export; note OPML support is deferred |

---

## Feature Dependencies

```
[Document Management]
    └──requires──> [Auth / User Accounts]

[Bullet CRUD + Nesting]
    └──requires──> [Document Management]

[Markdown Rendering]
    └──requires──> [Bullet CRUD + Nesting]

[#tag / @mention / !!date syntax]
    └──requires──> [Bullet CRUD + Nesting]
    └──enhances──> [Search]

[Tag Browser]
    └──requires──> [#tag syntax]

[Search (full-text)]
    └──requires──> [Bullet CRUD + Nesting]

[Zoom into Bullet]
    └──requires──> [Bullet CRUD + Nesting]

[Breadcrumb Navigation]
    └──requires──> [Zoom into Bullet]

[Collapse / Expand]
    └──requires──> [Bullet CRUD + Nesting]
    └──requires persistence──> [Auth / User Accounts]

[Server-Side Undo/Redo]
    └──requires──> [Bullet CRUD + Nesting]
    └──requires──> [Auth / User Accounts]
    └──NOTE: must be designed before bulk operations or it becomes very hard to retrofit]

[Drag-and-Drop Reorder (Desktop)]
    └──requires──> [Bullet CRUD + Nesting]
    └──requires──> [Undo/Redo] (drag is undoable)

[Swipe Gestures (Mobile)]
    └──requires──> [Bullet CRUD + Nesting]
    └──enhances──> [Drag-and-Drop Reorder] (mobile substitute)

[Complete Bullet]
    └──requires──> [Bullet CRUD + Nesting]

[Bulk Delete Completed]
    └──requires──> [Complete Bullet]
    └──requires──> [Undo/Redo] (bulk delete must be undoable)

[Bookmarks]
    └──requires──> [Bullet CRUD + Nesting]
    └──requires──> [Auth / User Accounts]

[Comments]
    └──requires──> [Bullet CRUD + Nesting]
    └──requires──> [Auth / User Accounts]

[File Attachments]
    └──requires──> [Bullet CRUD + Nesting]
    └──requires──> [Auth / User Accounts]

[Export as Markdown]
    └──requires──> [Document Management]
    └──requires──> [Bullet CRUD + Nesting]
```

### Dependency Notes

- **Undo/Redo must be designed early:** If added after drag-and-drop, bulk operations, or attachments, retrofitting the operation log to cover all those action types is painful. Architect the undo log schema before implementing any mutating operation.
- **Mobile gestures conflict with text selection long-press:** Long-press on mobile triggers native text selection. The context menu long-press must use a timer-based detection that cancels if text selection starts. This is a known implementation trap.
- **Collapse state requires Auth:** Collapse state is per-user, so auth must exist before persisting collapse state. Don't implement collapse with localStorage — it won't survive a device switch.
- **#tag / @mention / !!date enhance Search:** Once special syntax is rendered as chips, the search system should be able to filter by tag, mention, and date range — this shapes the search index schema from day one.

---

## MVP Definition

### Launch With (v1)

Minimum viable product — validates the core outliner loop.

- [ ] Auth (email/password + Google SSO) — gate everything behind accounts
- [ ] Document CRUD (create, rename, delete, flat list) — basic organization
- [ ] Bullet CRUD with unlimited nesting — the core product
- [ ] Indent / Outdent (Tab, Shift+Tab) — editing fundamentals
- [ ] Collapse / Expand (persisted) — navigation without zoom
- [ ] Zoom + Breadcrumb — makes deep nesting usable
- [ ] Drag-and-drop reorder (desktop) — essential for reorganization
- [ ] Ctrl+Up / Ctrl+Down move (keyboard) — keyboard-first power users
- [ ] Markdown rendering (bold, italic, strikethrough, inline code, links) — text richness
- [ ] Mark complete + bulk delete completed — task workflow
- [ ] Full-text search across all documents — retrieval
- [ ] #tag / @mention / !!date syntax + tag browser — light cross-referencing
- [ ] Bookmarks — navigation shortcuts
- [ ] Server-side undo/redo (50 steps, persisted) — safety net and differentiator
- [ ] File attachments per bullet — media capture
- [ ] Comments per bullet (flat) — annotation
- [ ] Mobile swipe gestures + long-press context menu — mobile usability
- [ ] Export document as Markdown — data portability
- [ ] Keyboard shortcuts (full set matching Dynalist defaults) — power user ergonomics
- [ ] New users start with Inbox document — onboarding

### Add After Validation (v1.x)

Features to add once core loop is confirmed working.

- [ ] PWA manifest + install prompt — makes mobile feel native without app store
- [ ] Dynalist/Workflowy Markdown import — migration path for existing users (once export format is validated)
- [ ] Date range filtering from !!date syntax — extends the date chip value
- [ ] Customizable keyboard shortcuts — power user quality-of-life
- [ ] List density options (cozy / compact) — personalization without complexity

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] Offline mode — requires service worker + sync conflict resolution; large scope
- [ ] Google Calendar sync for !!dates — useful but external API dependency
- [ ] OPML export — niche format; plain Markdown covers most use cases
- [ ] Folder hierarchy for documents — only if flat list proves insufficient at scale
- [ ] AI-powered search / summarize — evaluate after user base established

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Bullet CRUD + nesting + collapse | HIGH | MEDIUM | P1 |
| Indent / outdent + keyboard shortcuts | HIGH | LOW | P1 |
| Zoom + breadcrumb | HIGH | MEDIUM | P1 |
| Full-text search | HIGH | MEDIUM | P1 |
| Drag-and-drop reorder | HIGH | HIGH | P1 |
| Server-side undo/redo | HIGH | HIGH | P1 |
| Markdown rendering | HIGH | MEDIUM | P1 |
| Mark complete + bulk delete | MEDIUM | LOW | P1 |
| #tag / @mention / !!date syntax | MEDIUM | MEDIUM | P1 |
| Mobile swipe gestures | MEDIUM | MEDIUM | P1 |
| File attachments | MEDIUM | MEDIUM | P1 |
| Comments per bullet | MEDIUM | MEDIUM | P1 |
| Bookmarks | MEDIUM | LOW | P1 |
| Export as Markdown | MEDIUM | LOW | P1 |
| Tag browser pane | LOW | LOW | P2 |
| PWA manifest | LOW | LOW | P2 |
| Customizable shortcuts | LOW | MEDIUM | P2 |
| List density options | LOW | LOW | P2 |
| Dynalist import | LOW | MEDIUM | P3 |
| Offline mode | MEDIUM | HIGH | P3 |
| OPML export | LOW | MEDIUM | P3 |
| Bidirectional links | MEDIUM | HIGH | P3 (out of scope v1) |
| Collaboration / sharing | HIGH | HIGH | P3 (out of scope permanently) |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration or explicitly out of scope

---

## Competitor Feature Analysis

| Feature | Dynalist | Workflowy | Roam Research | Our Approach |
|---------|----------|-----------|---------------|--------------|
| Infinite nesting | Yes | Yes | Yes (block-based) | Yes — core |
| Zoom + breadcrumb | Yes | Yes | Yes | Yes — core |
| Collapse / expand | Yes | Yes | Yes | Yes, persisted in DB |
| Multiple documents | Yes | No (single outline) | Yes (pages) | Yes, flat list |
| Markdown | Yes (rich) | No (plain text) | Limited | Yes — WYSIWYG inline |
| #tags | Yes | Yes | Yes (linked refs) | Yes + @mentions + !!dates |
| Full-text search | Yes | Yes (instant) | Yes | Yes |
| File attachments | Yes (Pro only) | No | No | Yes, included free |
| Comments | No | No | Block comments | Yes, flat per bullet |
| Undo/redo | Session-only | Session-only | Session-only | Persisted 50 steps — differentiator |
| Export | OPML, HTML, text | OPML | Markdown, JSON | Markdown |
| Import | Workflowy OPML | None | — | Deferred to v1.x |
| Bookmarks | Yes | No | No | Yes |
| Mobile apps | iOS + Android | iOS + Android | iOS | Web only, responsive + gestures |
| Offline | Yes (desktop apps) | Yes (desktop apps) | No | Deferred |
| Self-hosted | No | No | No | Yes — core value |
| Paywall | Pro features gated | Limited free tier | $15/month | No paywall — all features free |
| Collaboration | Yes | Yes | Yes | Explicitly excluded |

---

## Sources

- [Dynalist Features Full List](https://dynalist.io/features/full)
- [Dynalist Keyboard Shortcut Reference](https://help.dynalist.io/article/91-keyboard-shortcut-reference)
- [Dynalist vs Workflowy Differences](https://help.dynalist.io/article/123-how-is-dynalist-different-from-workflowy)
- [Workflowy vs Dynalist Comparison — Slant](https://www.slant.co/versus/4412/15546/~workflowy_vs_dynalist)
- [10 Best Outliner Apps 2026 — bloggingx.com](https://bloggingx.com/best-outliner-apps/)
- [Dynalist User Reviews — Capterra](https://www.capterra.com/p/150742/Dynalist/reviews/)
- [Dynalist Forum: Product Discontinued Thread](https://talk.dynalist.io/t/product-discontinued/8761) (confirms Dynalist is in maintenance mode; Obsidian is the company's focus)
- [Evolution of Outliners](https://molodtsov.me/2020/07/outliners/)
- [Mobile Gesture Best Practices — Material Design 3](https://m3.material.io/foundations/interaction/gestures)
- [Touch Gesture Controls — Smashing Magazine](https://www.smashingmagazine.com/2017/02/touch-gesture-controls-mobile-interfaces/)

---

*Feature research for: Outliner / PKM web app (Dynalist/Workflowy clone, self-hosted)*
*Researched: 2026-03-09*
