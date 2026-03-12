---
phase: 11-bullet-tree
plan: "01"
subsystem: api
tags: [android, kotlin, retrofit, hilt, clean-architecture, tdd, bullet-tree]

# Dependency graph
requires:
  - phase: 10-document-management
    provides: DocumentApi pattern, DocumentRepositoryImpl try/catch Result<T> pattern, DI wiring (NetworkModule/DataModule), Reorderable library

provides:
  - BulletApi Retrofit interface with 11 endpoints
  - BulletRepository interface and BulletRepositoryImpl with Result<T> wrapping
  - 3 domain models: Bullet, FlatBullet, UndoStatus
  - 6 DTOs: BulletDto, CreateBulletRequest, PatchBulletRequest, MoveBulletRequest, UndoStatusDto, UndoCheckpointRequest
  - 10 use cases delegating to BulletRepository
  - FlattenTreeUseCase: pure Kotlin DFS tree flattener with collapse/zoom/depth-cap support
  - BulletTreeUiState sealed interface
  - BulletTreeViewModel scaffold with operation queue, load/state/reload, 17 stubbed operation signatures
  - DI wiring for BulletApi and BulletRepository in NetworkModule/DataModule

affects: 11-02, 11-03, 12-reactivity-and-polish

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "FlattenTreeUseCase: pure Kotlin DFS groupBy parentId, recursive with collapse skip and depth cap"
    - "BulletTreeViewModel operation queue: Channel(UNLIMITED) draining in init viewModelScope coroutine"
    - "BulletRepositoryImpl: same try/catch Result<T> wrapping as DocumentRepositoryImpl"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/data/api/BulletApi.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/BulletDto.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/CreateBulletRequest.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/PatchBulletRequest.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/MoveBulletRequest.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/UndoStatusDto.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/UndoCheckpointRequest.kt
    - android/app/src/main/java/com/gmaingret/notes/data/repository/BulletRepositoryImpl.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/model/Bullet.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/model/FlatBullet.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/model/UndoStatus.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/repository/BulletRepository.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/GetBulletsUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/CreateBulletUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/PatchBulletUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/DeleteBulletUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/IndentBulletUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/OutdentBulletUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/MoveBulletUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/UndoUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/RedoUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/GetUndoStatusUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/FlattenTreeUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeUiState.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModel.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/FlattenTreeUseCaseTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/di/NetworkModule.kt
    - android/app/src/main/java/com/gmaingret/notes/di/DataModule.kt

key-decisions:
  - "FlattenTreeUseCase gets @Inject constructor for Hilt compatibility — javax.inject is pure Java, does not add Android deps, still instantiable in unit tests without DI framework"
  - "PatchBulletRequest companion factory functions (updateContent, updateIsComplete, etc.) enforce single-field-per-request contract from server"
  - "BulletTreeViewModel.updateState preserves focusedBulletId across state rebuilds to prevent focus loss during silent reloads"

requirements-completed:
  - TREE-01
  - TREE-07

# Metrics
duration: 14min
completed: 2026-03-12
---

# Phase 11 Plan 01: Bullet Tree Data Layer Summary

**BulletApi (11 endpoints) + BulletRepositoryImpl + 10 use cases + FlattenTreeUseCase (TDD, 10 tests) + BulletTreeViewModel scaffold with Channel operation queue**

## Performance

- **Duration:** 14 min
- **Started:** 2026-03-12T13:25:52Z
- **Completed:** 2026-03-12T13:39:57Z
- **Tasks:** 2
- **Files modified:** 28 (26 created, 2 modified)

## Accomplishments
- Full bullet tree data layer: BulletApi with 11 Retrofit endpoints, 6 DTOs, BulletRepository interface, BulletRepositoryImpl with Result<T> wrapping, DI wiring in NetworkModule and DataModule
- FlattenTreeUseCase implemented TDD-style (10 tests: flat list, ordering, nesting, collapsed parent, zoom mode, depth cap, hasChildren) — all passing
- BulletTreeViewModel scaffold with @HiltViewModel, Channel(UNLIMITED) operation queue, loadBullets/updateState/reloadFromServer/loadUndoStatus fully implemented, 17 operation methods stubbed for Plan 02

## Task Commits

Each task was committed atomically:

1. **Task 1: Data layer — BulletApi, DTOs, repository, DI wiring** - `75f4841` (feat)
2. **Task 2 TDD RED: FlattenTreeUseCase tests** - `4070569` (test)
3. **Task 2 TDD GREEN: FlattenTreeUseCase implementation** - `d23238f` (feat)
4. **Task 2: Use cases, BulletTreeUiState, BulletTreeViewModel scaffold** - `045462f` (feat)

_Note: TDD task split into RED (test) + GREEN (feat) commits per TDD protocol_

## Files Created/Modified
- `data/api/BulletApi.kt` - Retrofit interface with 11 bullet endpoints
- `data/model/BulletDto.kt` - Server response DTO with toDomain() mapper
- `data/model/CreateBulletRequest.kt` - POST /api/bullets request body
- `data/model/PatchBulletRequest.kt` - PATCH /api/bullets/{id} body with single-field factory methods
- `data/model/MoveBulletRequest.kt` - POST /api/bullets/{id}/move body
- `data/model/UndoStatusDto.kt` - GET/POST undo status response DTO
- `data/model/UndoCheckpointRequest.kt` - POST /api/bullets/{id}/undo-checkpoint body
- `data/repository/BulletRepositoryImpl.kt` - Result<T>-wrapped Retrofit calls, sorted by position
- `domain/model/Bullet.kt` - Domain model with parentId, position, isCollapsed, note
- `domain/model/FlatBullet.kt` - Bullet + depth + hasChildren for LazyColumn rendering
- `domain/model/UndoStatus.kt` - canUndo/canRedo domain model
- `domain/repository/BulletRepository.kt` - 11-method interface returning Result<T>
- `domain/usecase/FlattenTreeUseCase.kt` - Pure Kotlin DFS, groupBy parentId, collapse/zoom/depth-cap
- `domain/usecase/GetBulletsUseCase.kt` through `GetUndoStatusUseCase.kt` - 10 thin delegation use cases
- `presentation/bullet/BulletTreeUiState.kt` - Loading/Success(bullets, flatList, focusedBulletId)/Error
- `presentation/bullet/BulletTreeViewModel.kt` - @HiltViewModel with queue, load, 17 stubs
- `test/.../FlattenTreeUseCaseTest.kt` - 10 unit tests (no Android deps)
- `di/NetworkModule.kt` - provideBulletApi added
- `di/DataModule.kt` - bindBulletRepository added

## Decisions Made
- FlattenTreeUseCase gets `@Inject constructor` for Hilt compatibility — `javax.inject` is pure Java, does not add Android dependencies, still instantiable in unit tests as `FlattenTreeUseCase()` without any DI framework
- `PatchBulletRequest` companion factory functions (`updateContent`, `updateIsComplete`, etc.) enforce the server's single-field-per-request constraint at the call site
- `BulletTreeViewModel.updateState` preserves `focusedBulletId` from current Success state across state rebuilds, preventing focus loss during silent server reloads

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] FlattenTreeUseCase missing @Inject constructor caused Hilt MissingBinding error**
- **Found during:** Task 2 verification (testDebugUnitTest run)
- **Issue:** Hilt could not inject FlattenTreeUseCase into BulletTreeViewModel — class had no @Inject constructor or @Provides method
- **Fix:** Added `@Inject constructor()` to FlattenTreeUseCase using `javax.inject.Inject` (pure Java, no Android deps). Tests still instantiate via `FlattenTreeUseCase()` directly.
- **Files modified:** `FlattenTreeUseCase.kt`
- **Verification:** testDebugUnitTest passes, compileDebugKotlin passes
- **Committed in:** d23238f (Task 2 TDD GREEN commit)

**2. [Rule 1 - Bug] Backtick test names with colons rejected by Android JUnit4**
- **Found during:** Task 2 TDD RED run
- **Issue:** Test names like `` `nested bullet: parent with two children` `` caused compile error "Name contains illegal characters: :"
- **Fix:** Removed colons from backtick test method names (3 test methods updated)
- **Files modified:** `FlattenTreeUseCaseTest.kt`
- **Verification:** All 10 tests compile and pass
- **Committed in:** 4070569 (TDD RED commit)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both were compile-time issues discovered during verification. No scope changes.

## Issues Encountered
None beyond the auto-fixed issues above.

## Next Phase Readiness
- All Plan 02 interfaces are stable: BulletRepository, all use cases, BulletTreeViewModel stubs
- FlattenTreeUseCase is tested and ready for use in Plan 02 optimistic update logic
- BulletTreeViewModel operation queue initialized and consuming — Plan 02 just fills the stub bodies
- No blockers identified for Plan 02 (ViewModel operations + tests)

---
*Phase: 11-bullet-tree*
*Completed: 2026-03-12*
