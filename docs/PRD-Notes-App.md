# PRD: Notes — Self-Hosted Personal Outliner

**Version:** 0.2
**Date:** February 2026
**Status:** Approved
**Author:** Greg

---

## Problem Statement

Power users who rely on outliner tools like Dynalist or WorkFlowy for organizing thoughts, tasks, and notes are dependent on third-party hosted services — with subscription fees, limited data control, and potential privacy concerns. There is no high-quality, self-hosted alternative that matches the fluid UX of these tools while providing full data sovereignty, offline access, and a first-class mobile experience. Without a self-hosted option, personal notes and structure live on someone else's infrastructure with no guarantee of longevity or privacy.

---

## Goals

1. Deliver a feature-complete outliner experience (nested bullets, zoom, breadcrumbs, multi-document) on both Android and web via a single Flutter codebase.
2. Support full offline usage with reliable, automatic sync when reconnected — no manual intervention required.
3. Enable fast capture and reorganization on mobile through gesture-based interactions without menus.
4. Maintain complete data ownership through a Docker-based self-hosted deployment with no third-party dependencies.
5. Achieve functional parity with core Dynalist features within the MVP phase.

---

## Non-Goals

| Non-Goal | Rationale |
|---|---|
| Multi-user collaboration / shared documents | Personal-use product; collaboration adds significant sync complexity |
| Native iOS app | Not in Greg's device ecosystem; Flutter web covers browser access |
| AI-powered features / smart suggestions | Out of scope for MVP; may revisit in V3+ |
| Public sharing / publishing of notes | No external audience; adds auth surface area |
| Custom themes / appearance settings | MVP ships with a single clean theme; design iteration comes later |
| Import from Dynalist/WorkFlowy | P1 nice-to-have; not blocking core value delivery |

---

## User Persona

**Greg — Solo Power User**

- Uses outliners daily for task management, note-taking, and project planning
- Self-hosts services at home; comfortable with Docker and basic server management
- Uses Android phone throughout the day and a web browser at his desk
- Values speed, keyboard fluency on web, and frictionless mobile gestures
- Wants his data local, private, and permanent

---

## User Stories

### Document Management

- As Greg, I want a sidebar listing all my documents so that I can navigate between separate areas of my life quickly.
- As Greg, I want to create, rename, and delete documents so that I can manage my note structure over time.
- As Greg, I want to zoom into any bullet and see a breadcrumb trail so that I can work deep in a hierarchy without losing context.

### Editing

- As Greg, I want to create infinitely nested bullet points so that I can capture hierarchical thoughts naturally.
- As Greg, I want inline Markdown formatting so that I can add emphasis and structure without leaving the keyboard.
- As Greg, I want to add `#tags` to any bullet so that I can cross-reference topics globally across all documents.
- As Greg, I want keyboard shortcuts (Tab, Shift+Tab, Enter, Ctrl+Z, Ctrl+F) so that I can edit at full speed on web.

### Mobile Interactions

- As Greg, I want to swipe right on a bullet to mark it complete so that I can triage tasks without opening menus.
- As Greg, I want to swipe left on a bullet to permanently delete it so that I can clean up items in one gesture (with a brief undo window).
- As Greg, I want to long-press the bullet point glyph to access a context menu so that I can reach all available actions.
- As Greg, I want to long-press the bullet text to enter drag-reorder mode so that I can reorganize without precision tapping.

### Search & Discovery

- As Greg, I want full-text search across all documents so that I can find any note instantly regardless of where it lives.
- As Greg, I want to tap a `#tag` to see all bullets tagged with it so that I can surface cross-document topics.

### Attachments

- As Greg, I want to attach images (from camera or gallery) to any bullet so that I can capture visual context.
- As Greg, I want to attach arbitrary files to bullets so that I can reference documents inline.
- As Greg, I want to record and attach audio memos to bullets so that I can capture thoughts hands-free.

### Sync & Offline

- As Greg, I want to write and edit notes while offline so that I can capture ideas anywhere without internet.
- As Greg, I want my changes to sync automatically when I reconnect so that my Android and web clients stay current.

### Authentication

- As Greg, I want to log in with my Google account so that I don't have to manage a separate password for the app.

---

## Requirements

### P0 — Must Have (MVP)

#### Core Outliner Engine
- Infinite nesting depth — no arbitrary limits
- Zoom into any bullet node (it becomes the root of the visible tree)
- Breadcrumb trail showing the full path from document root to current zoom level; each crumb is tappable to navigate back
- Collapse / expand subtrees
- Multiple named documents managed in a sidebar

#### Text Editing
- Press Enter to create a sibling bullet; Tab / Shift+Tab to indent / outdent
- Inline Markdown rendered live (WYSIWYG): `**bold**`, `*italic*`, `` `code` ``, `[text](url)` — raw syntax shown only while cursor is on that span
- `#tag` detection: tags are highlighted and tappable to filter; scope is **global across all documents**
- Undo / redo (Ctrl+Z / Ctrl+Y on web)

#### Web Keyboard Shortcuts
| Shortcut | Action |
|---|---|
| Enter | New bullet |
| Tab | Indent |
| Shift+Tab | Outdent |
| Ctrl+Z | Undo |
| Ctrl+Y | Redo |
| Ctrl+F | Open search |
| Ctrl+[ | Collapse node |
| Ctrl+] | Expand node |

#### Mobile Gestures
| Gesture | Action |
|---|---|
| Swipe right on bullet | Mark complete (strikethrough + dim) |
| Swipe left on bullet | **Permanently delete** (with undo snackbar, 5s — no trash/archive) |
| Long-press bullet glyph (●) | Open context menu |
| Long-press bullet text | Activate drag-to-reorder handle |

#### Context Menu (long-press bullet glyph)
- Indent / Outdent
- Move to another document
- Add attachment (image / file / audio)
- Duplicate
- Delete

#### Drag and Drop
- Drag to reorder within same parent
- Drag to reparent (change nesting level) with visual drop-zone indicators

#### Search
- Full-text search across all documents and all nesting levels
- Results show bullet text + document name + parent path
- `#tag` filter: tap any tag to show all matching bullets globally across all documents

#### Attachments
- Attach from camera, gallery, file picker, or audio recorder
- Inline image thumbnails within the bullet
- Tap image to view full-screen
- Non-image files shown as named chips with file type icon
- Audio attachments show a waveform / play button inline
- Files stored on self-hosted server filesystem

#### Offline Sync
- All writes go to local SQLite first; server sync is secondary
- Changes queue while offline and flush on reconnect automatically
- **Last-write-wins** conflict strategy: since this is a single-user app, true conflicts are not expected; the most recent timestamp wins silently
- No conflict resolution UI required — keeps the sync engine simple

#### Authentication
- Google OAuth 2.0 sign-in
- Session token persisted locally; silent refresh when valid
- Graceful degradation: remain usable offline when token refresh is not possible

#### Infrastructure
- Docker Compose deployment (app server + reverse proxy)
- SQLite database embedded in server container
- Server-side attachment storage with configurable volume mount
- Environment variable configuration (Google OAuth client ID/secret, attachment path, port)

---

### P1 — Nice to Have (MVP+ polish)

- Completed items section per document (collapsed by default; tap to expand)
- Secondary note field beneath a bullet (smaller text, also Markdown)
- Dark mode / light mode toggle
- Quick-capture floating action button (opens a minimal new-bullet dialog)
- Copy bullet as plain text or Markdown to clipboard
- Import from Dynalist (OPML or JSON export format)
- Export document as Markdown or OPML

---

### P2 — Future (V2)

- Voice activation shortcut to start audio recording without unlocking phone
- Speech-to-text transcription of audio attachments (on-device or server-side)
- Native Android app (Kotlin / Jetpack Compose replacing Flutter Android shell)
- Recurring bullet reminders / due dates
- Saved tag filters / smart lists

---

## Technical Considerations

> These are recommendations to be validated in the system design phase.

**Frontend:** Flutter (single codebase targeting Android and web)

**Backend:** Lightweight Dart (shelf) or Python (FastAPI) server — to be decided based on team preference. Thin API surface needed.

**Database:** SQLite on server (via embedded driver). Per-device local SQLite via `sqflite` (Android) and IndexedDB / OPFS (Flutter web).

**Sync Engine:** Simple timestamp-based sync. Each mutation carries a UTC timestamp. On reconnect, the client pushes its pending operation queue; the server applies all operations in timestamp order with last-write-wins semantics. No conflict resolution UI needed — single-user design means true conflicts are not expected in practice.

**Auth:** `google_sign_in` Flutter package on client; server validates Google ID tokens on each request.

**Attachment Storage:** Server-local filesystem with a configurable Docker volume. Clients upload via multipart POST; receive a stable URL for inline rendering.

**Deployment:** Docker Compose with two containers — app server and a reverse proxy (Caddy or nginx). HTTPS via Caddy's automatic TLS if domain is configured.

---

## Open Questions

| # | Question | Owner | Priority |
|---|---|---|---|
| 1 | What is the attachment size limit per file? Should the server enforce a total storage quota? | Greg | Medium |
| 2 | Should the sync engine use WebSockets (real-time push) or HTTP polling? | Engineering | Medium |
| 3 | How should offline Google SSO token refresh be handled — fail gracefully or block access? | Engineering | Medium |

**Resolved:**
- ~~Swipe-left delete: permanent (no archive/trash)~~ ✓
- ~~Tag scope: global across all documents~~ ✓
- ~~Markdown mode: live WYSIWYG~~ ✓
- ~~Sync conflict strategy: last-write-wins (single-user, conflicts not expected)~~ ✓

---

## Success Criteria

Since this is a personal tool, success is measured by reliability and daily usability rather than business metrics.

| Metric | Target |
|---|---|
| P0 feature completeness | 100% of P0 requirements passing on Android + web before ship |
| Sync reliability | Zero silent data loss across 30 days of daily active use |
| Offline resilience | Full read/write offline for 24+ hours; sync without manual intervention on reconnect |
| Render performance | List of 1,000+ bullets renders and scrolls at 60fps on mid-range Android |
| Cold start | App is interactive within 2 seconds on Android (warm start < 500ms) |

---

## Timeline & Phasing

No hard external deadline. Recommended phasing to de-risk the sync engine early:

| Phase | Scope |
|---|---|
| **Phase 1** | Core outliner (nested bullets, zoom, breadcrumb, multi-document) + Google SSO + basic online sync |
| **Phase 2** | Mobile gestures, drag-drop reordering, attachments |
| **Phase 3** | Full-text search, tag filtering, offline-first sync (last-write-wins) |
| **V2** | Voice capture, speech-to-text, native Android app |

---

*Next step: System Design — backend architecture, sync protocol, and data model.*
