---
phase: 12-reactivity-and-polish
plan: "01"
subsystem: android-data-layer
tags: [android, retrofit, hilt, coil, search, bookmarks, attachments]
dependency_graph:
  requires: []
  provides:
    - SearchApi, BookmarkApi, AttachmentApi (Retrofit interfaces)
    - SearchRepository, BookmarkRepository, AttachmentRepository (domain interfaces + impls)
    - SearchBulletsUseCase, GetBookmarksUseCase, AddBookmarkUseCase, RemoveBookmarkUseCase, GetAttachmentsUseCase
    - Coil ImageLoader wired with auth OkHttpClient
  affects:
    - Phase 12 UI plans (search, bookmarks, attachments screens)
tech_stack:
  added:
    - Coil 3.1.0 (coil-compose, coil-network-okhttp) — downgraded from 3.4.0 (requires Kotlin 2.3+, incompatible with project's Kotlin 2.1.20)
  patterns:
    - Retrofit interface + DTO + domain model + repository impl + use case stack
    - runCatching wrapping in repository impls for Result<T> returns
    - SingletonImageLoader.Factory on Application for auth-aware image loading
key_files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/data/api/SearchApi.kt
    - android/app/src/main/java/com/gmaingret/notes/data/api/BookmarkApi.kt
    - android/app/src/main/java/com/gmaingret/notes/data/api/AttachmentApi.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/SearchResultDto.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/BookmarkDto.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/AttachmentDto.kt
    - android/app/src/main/java/com/gmaingret/notes/data/repository/SearchRepositoryImpl.kt
    - android/app/src/main/java/com/gmaingret/notes/data/repository/BookmarkRepositoryImpl.kt
    - android/app/src/main/java/com/gmaingret/notes/data/repository/AttachmentRepositoryImpl.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/model/SearchResult.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/model/Bookmark.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/model/Attachment.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/repository/SearchRepository.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/repository/BookmarkRepository.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/repository/AttachmentRepository.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/SearchBulletsUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/GetBookmarksUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/AddBookmarkUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/RemoveBookmarkUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/GetAttachmentsUseCase.kt
  modified:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
    - android/app/src/main/java/com/gmaingret/notes/di/NetworkModule.kt
    - android/app/src/main/java/com/gmaingret/notes/di/DataModule.kt
    - android/app/src/main/java/com/gmaingret/notes/NotesApplication.kt
decisions:
  - "Coil downgraded to 3.1.0 — 3.4.0 pulls in Kotlin stdlib 2.3.10 which is incompatible with project Kotlin 2.1.20"
  - "AttachmentDto excludes userId and storagePath — server-only fields not needed client-side"
  - "downloadUrl hardcoded to https://notes.gregorymaingret.fr/api/attachments/{id}/file in toDomain()"
  - "AddBookmarkRequest data class defined in BookmarkApi.kt file — colocated with its sole consumer"
metrics:
  duration_seconds: 453
  completed_date: "2026-03-12"
  tasks_completed: 2
  files_created: 20
  files_modified: 5
---

# Phase 12 Plan 01: Data Layer for Search, Bookmarks, and Attachments Summary

Complete Retrofit + domain data layer giving all Phase 12 UI plans stable contracts for search, bookmarks, and attachment display via three API interfaces, three DTOs, three domain models, three repository pairs, five use cases, Hilt DI wiring, and Coil 3 configured with the auth-intercepted OkHttpClient.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add Coil dependency and create all data layer files | 2d6acfb | 22 files created/modified |
| 2 | Wire DI bindings and configure Coil ImageLoader | df442ae | 3 files modified |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Coil version downgraded from 3.4.0 to 3.1.0**
- **Found during:** Task 1 (first build attempt)
- **Issue:** Coil 3.4.0 transitively pulls in kotlin-stdlib 2.3.10 which is incompatible with the project's Kotlin compiler version 2.1.20 ("Module was compiled with an incompatible version of Kotlin: expected 2.1.0, got 2.3.0")
- **Fix:** Downgraded coil version in libs.versions.toml from 3.4.0 to 3.1.0, which uses Kotlin 2.1-compatible stdlib
- **Files modified:** android/gradle/libs.versions.toml

## Self-Check: PASSED

All 20 created files confirmed on disk. Both task commits (2d6acfb, df442ae) confirmed in git log. Build passes (`assembleDebug`) and unit tests pass (`testDebugUnitTest`).
