---
phase: 10-document-management
plan: "02"
subsystem: domain-presentation
tags: [kotlin, usecase, viewmodel, stateflow, sharedflow, hilt, tdd, mockk]

# Dependency graph
requires:
  - phase: 10-01
    provides: DocumentRepository interface, Document domain model, TokenStore.getLastDocId/saveLastDocId

provides:
  - GetDocumentsUseCase, CreateDocumentUseCase, RenameDocumentUseCase, DeleteDocumentUseCase, ReorderDocumentUseCase, OpenDocumentUseCase
  - MainUiState sealed interface (Loading/Success/Error/Empty)
  - Extended MainViewModel with full document state management
  - 7 test files with 27 passing unit tests

affects:
  - 10-03 (UI screens — inject MainViewModel, collect uiState)
  - 10-04 (drag-to-reorder UI — calls moveDocumentLocally / commitReorder)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Use case thin delegate: class XxxUseCase @Inject constructor(private val documentRepository: DocumentRepository)"
    - "OpenDocumentUseCase fire-and-forget: openDocument result ignored, saveLastDocId always called"
    - "MutableSharedFlow<String> for snackbar — collect before triggering in tests to avoid race"
    - "commitReorder revert: calls getDocumentsUseCase() on failure, emits snackbar via SharedFlow"
    - "deleteDocument auto-open: coerceAtMost(newList.size - 1) gives next or last remaining"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/GetDocumentsUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/CreateDocumentUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/RenameDocumentUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/DeleteDocumentUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/ReorderDocumentUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/OpenDocumentUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainUiState.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/GetDocumentsUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/CreateDocumentUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/RenameDocumentUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/DeleteDocumentUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/ReorderDocumentUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/OpenDocumentUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/presentation/main/MainViewModelTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainViewModel.kt

key-decisions:
  - "OpenDocumentUseCase.invoke() is Unit-returning (not Result<Unit>) — fire-and-forget semantics; API failure is silently swallowed, saveLastDocId always runs"
  - "SharedFlow snackbar test pattern: launch collect job before triggering failure, cancel after advanceUntilIdle — avoids UncompletedCoroutinesError from blocking first()"
  - "deleteDocument auto-open: deletedIndex.coerceAtMost(newList.size - 1) handles both middle deletion (opens next) and last-item deletion (opens previous)"
  - "commitReorder revert: reloads full list from API rather than maintaining a snapshot — simpler and always consistent with server state"

# Metrics
duration: 7min
completed: "2026-03-12"
---

# Phase 10 Plan 02: Document Use Cases and MainViewModel Summary

**6 document use cases with fire-and-forget OpenDocumentUseCase, MainUiState sealed hierarchy (Loading/Success/Error/Empty), and extended MainViewModel with cold-start, CRUD, optimistic reorder, and 27 passing unit tests**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-12
- **Completed:** 2026-03-12
- **Tasks:** 2
- **Files modified:** 15

## Accomplishments

- All 6 document use cases: thin delegates to DocumentRepository following @Inject constructor / operator invoke pattern
- OpenDocumentUseCase: fire-and-forget design — `openDocument()` API failure ignored, `saveLastDocId()` always called regardless
- MainUiState sealed interface with 4 variants: Loading (initial/skeleton), Success (documents + openDocumentId + inlineEditingDocId), Error (message), Empty
- Extended MainViewModel: loadDocuments with cold-start open logic, createDocument, submitRename, cancelRename, deleteDocument with auto-open, moveDocumentLocally, commitReorder with revert, refreshDocuments
- 27 unit tests across 7 test files — all passing, full test suite green

## Task Commits

Each task was committed atomically:

1. **Task 1: 6 document use cases with unit tests** - `172c5e0` (feat)
2. **Task 2: MainUiState and MainViewModel with document state and tests** - `477f935` (feat)

## Files Created/Modified

- 6 use case files under `domain/usecase/` — thin delegates to DocumentRepository
- `OpenDocumentUseCase.kt` — two methods: `invoke(id)` (fire-and-forget) and `getLastDocId()`
- `presentation/main/MainUiState.kt` — sealed interface with 4 states
- `presentation/main/MainViewModel.kt` — extended with all document operations
- 7 test files with complete coverage of use case delegation and ViewModel behaviors

## Decisions Made

- **OpenDocumentUseCase returns Unit not Result:** The open API call is fire-and-forget. The document IS open locally the moment the user taps it. Failure to hit the server endpoint does not change that. `saveLastDocId` must run in all cases.
- **SharedFlow snackbar test pattern:** `vm.snackbarMessage.first()` blocks indefinitely in tests because SharedFlow has no replay. Fixed by starting a `launch { collect {} }` job before the action that triggers emission, then cancelling after `advanceUntilIdle()`.
- **deleteDocument auto-open uses coerceAtMost:** `deletedIndex.coerceAtMost(newList.size - 1)` naturally selects the next document for mid-list deletions and the last remaining document when deleting the last item — no special-casing needed.
- **commitReorder revert loads from API:** Rather than caching a pre-move snapshot, failure reloads the full list from `getDocumentsUseCase()`. This guarantees server consistency and simplifies state management.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SharedFlow test blocked on first() call**
- **Found during:** Task 2
- **Issue:** `vm.snackbarMessage.first()` hangs forever in the commitReorder failure test because SharedFlow emits are not buffered and there is no subscriber at emission time.
- **Fix:** Changed test to `launch { vm.snackbarMessage.collect { ... } }` before triggering the failure, collect into a list, cancel after `advanceUntilIdle()`, then assert on the collected messages.
- **Files modified:** `MainViewModelTest.kt`
- **Commit:** `477f935`

## Next Phase Readiness

- All use cases are Hilt-injectable via `@Inject constructor`
- MainViewModel is ready for UI wiring in plans 10-03 and 10-04
- `snackbarMessage: SharedFlow<String>` available for UI to collect and show snackbars
- No blockers

---
*Phase: 10-document-management*
*Completed: 2026-03-12*
