---
phase: 01-foundation
plan: 05
subsystem: frontend
tags: [react, tanstack-query, dnd-kit, sidebar, documents, ui]
dependency_graph:
  requires: ["01-02", "01-03", "01-04"]
  provides: ["app-shell", "sidebar-ui", "document-management-ui"]
  affects: []
tech_stack:
  added: ["@dnd-kit/core (drag-to-reorder)", "@dnd-kit/sortable (sortable list)"]
  patterns: ["TanStack Query mutations with optimistic updates", "CSS hover-reveal for contextual menus", "apiClient.download() for blob responses"]
key_files:
  created:
    - client/src/hooks/useDocuments.ts
    - client/src/pages/AppPage.tsx
    - client/src/components/Sidebar/Sidebar.tsx
    - client/src/components/Sidebar/DocumentList.tsx
    - client/src/components/Sidebar/DocumentRow.tsx
    - client/src/components/DocumentView/DocumentView.tsx
  modified:
    - client/src/App.tsx
    - client/src/api/client.ts
    - client/src/index.css
decisions:
  - "3-dot menu hidden by default; revealed via CSS opacity on hover/focus-within (locked UX decision)"
  - "Sidebar fixed at 240px non-resizable (locked UX decision)"
  - "Document rows show name only, no metadata (locked UX decision)"
  - "apiClient.download() added for blob/file responses; avoids storing token in localStorage"
  - "Optimistic reorder: DnD updates list immediately, reverts on error, settles with server refetch"
metrics:
  duration: "~2 minutes"
  completed_date: "2026-03-09"
  tasks_completed: 2
  files_created: 6
  files_modified: 3
---

# Phase 1 Plan 05: App Shell and Document Management UI Summary

**One-liner:** Two-panel app shell with full sidebar document management (create, rename, delete, drag-to-reorder, export) using TanStack Query and @dnd-kit.

## What Was Built

### Task 1: Document hooks and AppPage layout

- `client/src/hooks/useDocuments.ts` — Eight TanStack Query hooks covering all document operations: `useDocuments`, `useCreateDocument`, `useRenameDocument`, `useDeleteDocument`, `useReorderDocument` (with optimistic update), `useOpenDocument`, `useExportDocument`, `useExportAllDocuments`
- `client/src/api/client.ts` — Added `download(path)` method returning raw `Response` for blob handling without storing tokens in localStorage
- `client/src/pages/AppPage.tsx` — Two-panel layout: Sidebar (240px) + main content; navigates to last opened doc on load; calls `/api/documents/:id/open` on doc change
- `client/src/App.tsx` — Updated routes: `/login`, `/`, `/doc/:docId` (both protected), catch-all redirects to `/`

### Task 2: Sidebar, DocumentList, DocumentRow, DocumentView

- `Sidebar.tsx` — Fixed 240px shell; header with `+` (new doc) and `⋯` (sidebar menu with Export All + Logout); mobile overlay for slide-over behavior
- `DocumentList.tsx` — DnD-kit `DndContext` + `SortableContext`; `PointerSensor` with 5px activation distance to prevent accidental drags; drag-end calls `PATCH /api/documents/:id/position` with `{ afterId }`
- `DocumentRow.tsx` — Name-only display; 3-dot menu with Rename (inline input), Export as Markdown, Delete (with `window.confirm`); dnd-kit `useSortable` handles drag handles
- `DocumentView.tsx` — Phase 1 placeholder: document title + single contenteditable bullet; Phase 2 will replace with real bullet tree
- `index.css` — `.document-row .row-menu-trigger` hidden by default; revealed on `:hover` and `:focus-within`

## Deviations from Plan

None — plan executed exactly as written. The `apiClient.download()` method was already anticipated in the plan spec and implemented as described.

## Self-Check: PASSED

Files exist:
- client/src/hooks/useDocuments.ts: FOUND
- client/src/pages/AppPage.tsx: FOUND
- client/src/components/Sidebar/Sidebar.tsx: FOUND
- client/src/components/Sidebar/DocumentList.tsx: FOUND
- client/src/components/Sidebar/DocumentRow.tsx: FOUND
- client/src/components/DocumentView/DocumentView.tsx: FOUND

Commits exist:
- 84e6919: feat(01-05): document hooks, AppPage layout, and updated routes
- 31f3b0e: feat(01-05): Sidebar, DocumentList (dnd-kit), DocumentRow (3-dot menu), DocumentView

TypeScript: `npx tsc --noEmit` exits 0.
