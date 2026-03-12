---
phase: 10-document-management
plan: "01"
subsystem: api
tags: [kotlin, retrofit, hilt, datastore, document, reorderable]

# Dependency graph
requires:
  - phase: 09-android-foundation-and-auth
    provides: TokenStore, AuthRepositoryImpl pattern, NetworkModule/DataModule DI wiring, Retrofit singleton

provides:
  - Document domain model (id, title, position as Double)
  - DocumentApi Retrofit interface with all 6 document endpoints
  - DocumentDto, CreateDocumentRequest, RenameDocumentRequest, ReorderDocumentRequest DTOs
  - DocumentRepository interface (6 network ops returning Result<T> + 2 local ops)
  - DocumentRepositoryImpl with try/catch Result wrapping and position-sorted getDocuments()
  - TokenStore.saveLastDocId / getLastDocId (plain DataStore, non-encrypted)
  - Hilt wiring: provideDocumentApi in NetworkModule, bindDocumentRepository in DataModule
  - Reorderable 3.0.0 dependency available for drag-to-reorder UI

affects:
  - 10-02 (use cases layer — depends on DocumentRepository interface)
  - 10-03 (ViewModel — injects DocumentRepository)
  - 10-04 (UI — uses Reorderable, depends on all layers)
  - 11-bullet-tree (builds on document model and repository pattern)

# Tech tracking
tech-stack:
  added:
    - "sh.calvin.reorderable:reorderable 3.0.0 — drag-to-reorder for Compose lazy lists"
  patterns:
    - "DocumentApi follows AuthApi Retrofit interface pattern exactly"
    - "DocumentRepositoryImpl follows AuthRepositoryImpl try/catch Result<T> pattern"
    - "Response<Unit> return type for 204 No Content endpoints (openDocument, deleteDocument)"
    - "Plain DataStore read/write for non-sensitive UUIDs (lastDocId); Tink only for auth tokens"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/domain/model/Document.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/repository/DocumentRepository.kt
    - android/app/src/main/java/com/gmaingret/notes/data/api/DocumentApi.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/DocumentDto.kt
    - android/app/src/main/java/com/gmaingret/notes/data/repository/DocumentRepositoryImpl.kt
  modified:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
    - android/app/src/main/java/com/gmaingret/notes/data/local/TokenStore.kt
    - android/app/src/main/java/com/gmaingret/notes/di/NetworkModule.kt
    - android/app/src/main/java/com/gmaingret/notes/di/DataModule.kt

key-decisions:
  - "openDocument and deleteDocument return Response<Unit> (not Unit) — Gson converter throws on 204 empty body with plain Unit return type"
  - "getDocuments() sorts by position ascending in the repository — avoids relying on server-side ORDER BY guarantee and keeps sort logic in one place"
  - "lastDocId stored plain (no Tink encryption) — it is a non-sensitive UUID; clearAll() covers it automatically via DataStore.edit { it.clear() }"
  - "Reorderable 3.0.0 added now as a dependency — UI layers (plan 10-04) will use it without needing build changes"

patterns-established:
  - "Response<Unit> pattern: use for any endpoint that returns 204 No Content with empty body"
  - "Result wrapping: every repository method wraps network call in try/catch, returns Result.success / Result.failure"
  - "Plain DataStore for non-sensitive data; Tink encryption only for tokens and email"

requirements-completed:
  - DOCM-01
  - DOCM-06

# Metrics
duration: 9min
completed: "2026-03-12"
---

# Phase 10 Plan 01: Document Data Layer Summary

**Retrofit DocumentApi with 6 endpoints, DocumentRepository with Result<T> wrapping, Hilt DI wiring, and plain DataStore lastDocId persistence using Reorderable 3.0.0**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-12T11:20:06Z
- **Completed:** 2026-03-12T11:23:10Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments

- Full document data layer: domain model, DTOs, Retrofit API interface (6 endpoints), repository interface and implementation
- All network calls wrapped in try/catch returning Result<T> — ViewModels never handle exceptions directly
- lastDocId persistence added to TokenStore using plain DataStore (not Tink-encrypted) since doc IDs are non-sensitive UUIDs
- Hilt DI fully wired: NetworkModule provides DocumentApi, DataModule binds DocumentRepository
- Reorderable 3.0.0 dependency added for drag-to-reorder UI in later plans

## Task Commits

Each task was committed atomically:

1. **Task 1: Domain model, API interface, DTOs, and Reorderable dependency** - `97aeafb` (feat)
2. **Task 2: Repository interface, implementation, DI wiring, and lastDocId persistence** - `36d426b` (feat)

**Plan metadata:** `88aa3a6` (docs: complete plan)

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/notes/domain/model/Document.kt` - Domain model: id, title, position (Double for FLOAT8 midpoint)
- `android/app/src/main/java/com/gmaingret/notes/domain/repository/DocumentRepository.kt` - Interface with 6 Result<T> network ops + 2 local ops
- `android/app/src/main/java/com/gmaingret/notes/data/api/DocumentApi.kt` - Retrofit interface; openDocument/deleteDocument return Response<Unit> for 204
- `android/app/src/main/java/com/gmaingret/notes/data/model/DocumentDto.kt` - DTO with toDomain() mapper plus 3 request classes
- `android/app/src/main/java/com/gmaingret/notes/data/repository/DocumentRepositoryImpl.kt` - Production impl; sorts getDocuments() by position ascending
- `android/app/src/main/java/com/gmaingret/notes/data/local/TokenStore.kt` - Added saveLastDocId / getLastDocId (plain DataStore)
- `android/app/src/main/java/com/gmaingret/notes/di/NetworkModule.kt` - Added provideDocumentApi
- `android/app/src/main/java/com/gmaingret/notes/di/DataModule.kt` - Added bindDocumentRepository
- `android/gradle/libs.versions.toml` - Added reorderable 3.0.0 version and library entry
- `android/app/build.gradle.kts` - Added implementation(libs.reorderable)

## Decisions Made

- **Response<Unit> for 204 endpoints:** openDocument and deleteDocument return `Response<Unit>` instead of plain `Unit` because Retrofit's Gson converter throws on an empty body when the return type is `Unit`. Using `Response<Unit>` lets the repository check `isSuccessful` without parsing.
- **Sort in repository:** `getDocuments()` maps DTOs to domain objects then sorts by position ascending. This ensures consistent ordering regardless of server-side ORDER BY changes.
- **Plain DataStore for lastDocId:** Doc IDs are non-sensitive UUIDs. Running them through Tink AES-GCM adds latency with no security benefit. `clearAll()` covers the key automatically via `it.clear()`.
- **Reorderable dependency added now:** The library is a build dependency, not runtime behavior. Adding it in plan 01 (data layer) avoids a build-config change mid-UI development.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required. Reorderable is a Maven Central library that resolves automatically via Gradle.

## Next Phase Readiness

- Complete data layer is now injectable via Hilt for use cases and ViewModels
- DocumentRepository interface is the only dependency needed by plan 10-02 (use cases)
- Reorderable library is available for plan 10-04 (drag-to-reorder UI)
- No blockers

---
*Phase: 10-document-management*
*Completed: 2026-03-12*
