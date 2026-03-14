<objective>
Implement the document management layer for the Notes Android app. Build a Material 3 navigation drawer with the document list, support creating/renaming/deleting documents, drag-to-reorder, and navigation to a document view screen. The document view will show a placeholder for now (bullet tree comes in prompt 3).

At the end of this phase, the user can open the drawer, see their documents, create new ones, rename/delete via long-press menu, reorder by dragging, and tap to navigate to a document view.
</objective>

<context>
This builds on the Android project from prompt 001. The auth layer, Hilt DI, Retrofit, and navigation are already set up.

Read these files to understand the document API contract and patterns:
- `server/src/routes/documents.ts` — CRUD endpoints, position reorder logic
- `client/src/hooks/useDocuments.ts` — API calls, optimistic update patterns
- `client/src/components/Sidebar/DocumentList.tsx` — document list UI, drag reorder

Document API:
- `GET /api/documents` → `[{ id, title, position, lastOpenedAt, createdAt, updatedAt }]`
- `POST /api/documents` with `{ title? }` → new document (server creates "Untitled" if no title)
- `PATCH /api/documents/:id` with `{ title }` → renamed document
- `DELETE /api/documents/:id` → 204
- `PATCH /api/documents/:id/position` with `{ afterId }` → server computes float midpoint position
- `POST /api/documents/:id/open` → updates lastOpenedAt (for sorting/resume)

Documents are sorted by `position` (float). The "Inbox" document is auto-created on registration.
</context>

<requirements>
1. **DocumentApi** Retrofit interface with all endpoints above
2. **DocumentRepository** with `StateFlow<List<Document>>` — fetches on init, exposes reactive list
3. **Optimistic updates**:
   - Create: add to list immediately with temp ID, replace on server response
   - Rename: update title in list immediately, rollback on error
   - Delete: remove from list immediately, rollback on error + show snackbar
   - Reorder: move item in list immediately, call API, rollback on error
4. **DocumentListViewModel** exposing `UiState<List<Document>>` (Loading/Success/Error)
5. **MainScreen** with `ModalNavigationDrawer`:
   - Drawer content: user email at top, document list, "New Document" FAB/button, logout button
   - Content: nested navigation showing document view
6. **DocumentRow** composable: title, highlight if active, tap to navigate
7. **Long-press context menu** (or bottom sheet) on document: Rename, Delete
8. **Rename dialog**: Material 3 AlertDialog with pre-filled text field
9. **Delete confirmation**: AlertDialog with confirm/cancel
10. **Drag-to-reorder**: Long-press drag on document rows to reorder. On drop, compute `afterId` (the document directly above the new position, or null if first) and call the position endpoint.
11. **Last opened persistence**: Save last opened document ID in DataStore/SharedPreferences. On app start, auto-navigate to it.
12. **Pull-to-refresh** on document list
</requirements>

<implementation>
New/modified files:
```
data/
  remote/
    api/DocumentApi.kt
    dto/DocumentDtos.kt              — CreateDocRequest, RenameRequest, ReorderRequest, DocumentDto
  repository/DocumentRepository.kt

domain/
  model/Document.kt                  — data class(id, title, position, lastOpenedAt, createdAt)
  repository/IDocumentRepository.kt

presentation/
  documents/
    DocumentListViewModel.kt
    DocumentListScreen.kt            — Drawer content with LazyColumn
    DocumentRow.kt                   — Single document row
  main/
    MainScreen.kt                    — Updated: ModalNavigationDrawer + scaffold
    MainViewModel.kt                 — Selected doc, drawer state, last-opened
  document/
    DocumentScreen.kt                — Placeholder: shows doc title + "Bullets coming soon"
  navigation/
    NavGraph.kt                      — Updated: main/{docId} route
```

For drag-to-reorder, use `Modifier.pointerInput` with `detectDragGesturesAfterLongPress` or the `org.burnoutcrew.composereorderable:reorderable` library. Keep it simple — the document list is typically small (< 50 items).

After reorder: determine the document directly above the moved document's new position. Send its `id` as `afterId` to `PATCH /api/documents/:id/position`. If moved to first position, send `afterId: null`.

The drawer should feel native — `ModalNavigationDrawer` with `DrawerState` controlled by a hamburger menu icon in the top bar. On document tap: navigate + close drawer.
</implementation>

<verification>
1. App builds and runs
2. Drawer opens with document list sorted by position
3. Tap document → navigates to document view (placeholder), drawer closes
4. "New Document" → document appears in list
5. Long-press → Rename → dialog with current title → save updates title
6. Long-press → Delete → confirmation → document removed
7. Drag reorder → positions update, persist after refresh
8. Kill and reopen app → auto-navigates to last opened document
9. Pull-to-refresh reloads document list
10. Network error → snackbar with error message, list shows previous data
</verification>

<success_criteria>
- Document CRUD works end-to-end against production backend
- Optimistic updates feel instant — no lag between action and UI update
- Drag reorder is smooth with proper visual feedback
- Error states handled gracefully (rollback + snackbar)
- Navigation state preserved across configuration changes
</success_criteria>
