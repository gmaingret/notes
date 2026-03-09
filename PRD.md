# Notes - Product Requirements Document (PRD)

## Overview
Notes is a self-hosted, multi-user clone of Dynalist/Workflowy without paywalls. It delivers the core infinite bullet-point outlining experience as a web application fully usable on both desktop and mobile browsers. Data is completely private per user with no sharing or collaboration.

**Target users**: Individuals needing powerful personal knowledge management with bullet reordering, comments, attachments, search, and zoom focus.

**Platform**: Web application (responsive — fully usable on mobile browsers with touch-friendly controls).

**Key differentiators**: No paywalls, self-hosted, mobile-friendly touch interactions, persisted undo history.

---

## User Authentication
- **Methods**: Google SSO + Email/Password.
- **Registration**: Open to anyone (no invites/admins).
- **Sessions**: Standard login persistence with logout button in sidebar.
- **No roles**: All users equal.
- **Configuration**: OAuth credentials provided via `.env` file (see `.env.example`).

---

## Data Model
```
User
└── Documents (multiple, flat list - no folders)
    └── Root Bullet
        ├── Children bullets (unlimited nesting)
        ├── Text content (markdown)
        ├── Completion state (transparent/strikethrough when done)
        ├── Comments (flat list, plain text)
        ├── Attachments (files)
        ├── Bookmarks (personal flag)
        └── Metadata (tags, !!dates, collapse state)
```

- **Full isolation**: Each user's documents completely private.
- **No sharing**: No workspaces, document sharing, or collaboration.
- **Soft delete**: Bullets are soft-deleted (undo available). Permanently purged only via "Bulk delete completed".

---

## Core UI Layout

```
[No-focus toolbar: sidebar | search | bookmarks | hide completed | bulk delete completed]

[Main canvas: infinite scrolling bullet list]
    ↑ Bullet glyph click = zoom to full screen root
    ↑ Breadcrumb bar (click ancestor → zoom out to that level)

[Focus toolbar (appears above keyboard when bullet selected)]
Desktop:  indent | outdent | ↑ | ↓ | undo | redo | attachment | comment | bookmark
Mobile:   indent | outdent | ↑ | ↓ | undo | redo | attachment | comment | bookmark | complete | delete

[Left sidebar - hidden by default, toggle via toolbar]
Tab 1: Documents (list + "New Document")
Tab 2: Tag Browser (list of tags + bullet counts)
- Rename/reorder/delete documents
- Logout button
```

**Desktop specifics**:
- Collapsible sidebar (hidden by default).
- Full keyboard-driven navigation.
- Right-click context menu on bullets.

**Mobile specifics**:
- Swipe right on bullet = mark complete (strikethrough + 50% opacity).
- Swipe left on bullet = delete (soft delete).
- Long press bullet = context menu.
- Focus toolbar includes complete/delete buttons (no Tab/Shift-Tab on mobile, no right-click).
- Touch-friendly bullet drag handles for reordering.

---

## Document Management
- **List view**: Sidebar Tab 1 shows flat list of user documents.
- **Actions** (from sidebar): New Document, Rename, Reorder (drag), Delete.
- **Navigation**: Click document → loads as main canvas.
- **Default**: New users start with one blank "Inbox" document.

---

## Bullet Point Editing
**Core interactions** (Dynalist-style):
- **Reorder**: Drag bullets (maintains children).
- **Indent/Outdent**: Toolbar buttons, context menu, or keyboard shortcuts (desktop). Toolbar buttons or context menu (mobile).
- **New bullet**: Enter key (at end of line).
- **Collapse**: Chevron glyph on bullets with children (persisted per user).
- **Zoom focus**: Click bullet glyph → shows as full-screen root with breadcrumb above.
- **Breadcrumb**: Click any ancestor → zooms out to make it the new root.

**Completion**:
- Mark complete → strikethrough + 50% opacity, stays in position.
- "Hide completed" toggle in no-focus toolbar.
- Bulk delete completed (irreversible, permanently purges).

**Mobile touch interactions**:
- Swipe right = complete.
- Swipe left = delete (soft delete, undo available).
- Long press = context menu: indent, outdent, move up/down, complete, delete, bookmark, attachment, comment.

**Context menus/toolbars**:
```
Focus toolbar (bullet selected):
  Desktop: indent | outdent | ↑ | ↓ | undo | redo | attachment | comment | bookmark
  Mobile:  indent | outdent | ↑ | ↓ | undo | redo | attachment | comment | bookmark | complete | delete

No-focus toolbar: sidebar | search | bookmarks | hide completed | bulk delete completed
```

---

## Markdown Rendering
**Live toggle rendering** (like Dynalist):
- Type `*text*` → temporarily shows raw markdown while editing.
- Finish editing (blur/Enter/Esc) → renders final markdown.

**Supported syntax**:
- `**bold**`
- `*italic*`
- `~~strikethrough~~`
- `[link](https://...)`
- `![image](https://...)` (renders inline preview)

---

## Special Syntax (Searchable)
All render as clickable chips/badges and feed into search + tag browser:

- **`#tag`** → Creates tag, appears in sidebar Tag Browser.
- **`@person`** → Personal label (e.g. `@greg`, `@projectX`).
- **`!!`** → Opens date/time picker → renders as `📅 Mar 9, 2026 14:30` badge.

**Tag browser** (Sidebar Tab 2):
- List of all unique `#tags`, `@person`, `!!dates` across user's documents.
- Click tag → filtered main view (all matching bullets).
- Shows bullet counts.

---

## Comments
**Dynalist-style**:
- **Indicator**: Small note icon appears on bullets with comments.
- **Access**: Click icon → opens side panel (right edge slide-in).
- **Content**: Plain text only, flat list (no threading).
- **Actions**: Add new comment, delete own comments.

---

## Attachments
- **Upload**: Via focus toolbar or context menu.
- **Limits**: Any file type, 100MB max per file.
- **Storage**: Docker volume mount.
- **Preview**:
  - Images: Inline rendering in bullet.
  - PDFs: Thumbnail preview.
  - Others: Download icon + filename.

---

## Search
- **Scope**: All user's documents.
- **Query**: Full-text + tags (`#tag`), people (`@person`), dates (`!!2026-03-09`).
- **Results**: List of matching bullets → click opens in zoomed focus view.
- **Access**: No-focus toolbar button → overlay/search modal.

---

## Bookmarks
- **Scope**: Personal per user.
- **Set**: Via focus toolbar or context menu.
- **Access**: No-focus toolbar → dedicated screen showing bookmarked bullets.
- **Behavior**: Click bookmark → opens bullet in zoomed focus view.

---

## Undo/Redo
- **Scope**: Global per user (not per document), 50 levels deep.
- **Coverage**: Text edits + structural changes (indent/reorder/delete).
- **Persistence**: Server-side, survives page refresh and app restart.
- **Delete undo**: Restoring a deleted bullet restores all its children.
- **Access**: Focus toolbar buttons + keyboard shortcuts.

---

## Keyboard Shortcuts (Desktop)
```
Navigation
  Enter           = new bullet below
  Tab             = indent
  Shift+Tab       = outdent
  Ctrl/Cmd+↑↓     = move bullet up/down
  Ctrl/Cmd+]      = zoom in (to focused bullet)
  Ctrl/Cmd+[      = zoom out (to parent)

Editing
  Ctrl/Cmd+Z      = undo
  Ctrl/Cmd+Y      = redo
  Ctrl/Cmd+B      = bold
  Ctrl/Cmd+I      = italic

Global
  Ctrl/Cmd+P      = search
  Ctrl/Cmd+*      = bookmarks
  Ctrl/Cmd+E      = toggle sidebar
```

---

## Export
- **Format**: Full document tree as Markdown file (bullets → `- `, preserves nesting via indentation).
- **Scope**: Single document or all user documents.
- **Access**: Document context menu in sidebar + bulk export option.

---

## Non-Goals (v1)
- Native mobile apps
- Offline mode
- Push notifications
- Shared documents/workspaces
- Folders
- Agenda/calendar view
- Import from Dynalist/Workflowy
- Threaded comments
- Version history beyond 50-step undo
- Admin panel
- Custom domains
