---
phase: 13-widget-foundation
plan: "04"
subsystem: android-widget
tags: [widget, navigation, deep-link, testing, unit-test]
dependency_graph:
  requires: [13-01, 13-02, 13-03]
  provides: [widget-to-app-navigation, onDeleted-test-coverage]
  affects: [WidgetUiState, NotesWidget, WidgetContent, MainActivity, MainScreen]
tech_stack:
  added: []
  patterns: [mockkObject-companion-singleton, Robolectric-Glance-receiver-test]
key_files:
  created:
    - android/app/src/test/java/com/gmaingret/notes/widget/NotesWidgetReceiverTest.kt (expanded with Robolectric onDeleted test)
  modified:
    - android/app/src/main/java/com/gmaingret/notes/widget/WidgetUiState.kt
    - android/app/src/main/java/com/gmaingret/notes/widget/NotesWidget.kt
    - android/app/src/main/java/com/gmaingret/notes/widget/WidgetContent.kt
    - android/app/src/main/java/com/gmaingret/notes/MainActivity.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainScreen.kt
    - android/app/src/test/java/com/gmaingret/notes/widget/NotesWidgetTest.kt
decisions:
  - "consumeWidgetDocumentId() pattern chosen over StateFlow for simplicity — returns-and-clears atomically, no new state propagation needed"
  - "Robolectric required for NotesWidgetReceiverTest.onDeleted because GlanceAppWidgetReceiver.onDeleted calls BroadcastReceiver.goAsync() which is not available in plain JVM tests"
  - "mockkObject(WidgetStateStore) intercepts companion getInstance without real DataStore/Tink — benign NPE in Glance coroutine cleanup does not affect test assertions"
metrics:
  duration: "~20 minutes"
  completed_date: "2026-03-14"
  tasks_completed: 2
  files_changed: 7
---

# Phase 13 Plan 04: Widget-to-App Navigation and onDeleted Test Coverage Summary

**One-liner:** Fixed widget tap navigation by wiring documentId through WidgetUiState.Content to MainActivity deep link handler, and added Robolectric unit test for onDeleted cleanup.

## What Was Built

Three coordinated changes to close 3 verification gaps from 13-VERIFICATION.md:

1. **WidgetUiState.Content now carries documentId** — Added `val documentId: String` as the first field, enabling the document context to flow from `fetchWidgetData` all the way to the tap intent.

2. **End-to-end widget tap navigation** — `NotesWidget.fetchWidgetData` now passes `docId` into `Content(documentId = docId, ...)`. `ContentView` forwards `state.documentId` to both `HeaderRow` and `BulletRow` (replacing the previous hardcoded `""` values). `MainActivity` reads `OPEN_DOCUMENT_ID` from the intent in both `onCreate` and `onNewIntent`, storing it in `pendingWidgetDocId`. `MainScreen` consumes it via `LaunchedEffect(Unit)` calling `activity?.consumeWidgetDocumentId()?.let { viewModel.openDocument(it) }`.

3. **onDeleted unit test** — `NotesWidgetReceiverTest` now uses `@RunWith(RobolectricTestRunner::class)` and `mockkObject(WidgetStateStore)` to verify that `clearDocumentId` is called once for each widget ID passed to `onDeleted`.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Wire documentId through widget UI and add MainActivity deep link handling | e0c2797 | WidgetUiState.kt, NotesWidget.kt, WidgetContent.kt, MainActivity.kt, MainScreen.kt, NotesWidgetTest.kt |
| 2 | Add onDeleted cleanup unit test to NotesWidgetReceiverTest | 176d750 | NotesWidgetReceiverTest.kt |

## Verification Results

- No `documentId = ""` in WidgetContent.kt (grep returns empty)
- `OPEN_DOCUMENT_ID` present in MainActivity.kt at import and usage sites
- All widget tests pass: 8/8 NotesWidgetTest, 2/2 NotesWidgetReceiverTest
- Full test suite passes (BUILD SUCCESSFUL)
- APK builds successfully (assembleDebug: BUILD SUCCESSFUL)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] First onDeleted test approach failed without Robolectric**
- **Found during:** Task 2, first attempt
- **Issue:** `GlanceAppWidgetReceiver.onDeleted` calls `BroadcastReceiver.goAsync()` which is not mocked in plain JVM tests — threw `RuntimeException: Method goAsync in android.content.BroadcastReceiver not mocked`
- **Fix:** Added `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [28])` to the test class, imported `ApplicationProvider.getApplicationContext()` for context parameter
- **Files modified:** NotesWidgetReceiverTest.kt
- **Commit:** 176d750 (incorporated into task commit)

## Key Decisions

1. **consumeWidgetDocumentId() over StateFlow:** The activity exposes a simple `fun consumeWidgetDocumentId(): String?` that returns-and-clears the pending ID. This avoids threading a new StateFlow through NotesApp/NavGraph and keeps the change minimal.

2. **Robolectric required for onDeleted test:** `GlanceAppWidgetReceiver.onDeleted` dispatches an async coroutine via `goAsync()`. Robolectric provides the Android broadcast machinery needed for this to not crash. A benign `NullPointerException` in the Glance coroutine cleanup (from Robolectric's mock `PendingResult`) appears in stderr but does not affect test assertions — all three `coVerify` checks execute before the coroutine cleanup runs.

3. **mockkObject(WidgetStateStore) for singleton interception:** The companion object's `getInstance` is intercepted without needing real DataStore or Tink AEAD initialization.

## Self-Check: PASSED

All key files verified present. Both task commits (e0c2797, 176d750) confirmed in git log.
