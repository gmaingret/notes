---
phase: 13-widget-foundation
verified: 2026-03-14T12:30:00Z
status: human_needed
score: 12/12 must-haves verified
re_verification: true
  previous_status: gaps_found
  previous_score: 9/12
  gaps_closed:
    - "Tapping the document title opens the full Notes app to that document"
    - "Tapping a bullet row opens the full Notes app to the document"
    - "NotesWidgetReceiver.onDeleted cleans up WidgetStateStore for each deleted widget ID (verified by unit test)"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Tap widget title and bullet rows on a real Android device"
    expected: "App opens and navigates directly to the configured document (not the default Notes list)"
    why_human: "LaunchedEffect(Unit) in MainScreen fires once on composition entry — correct runtime behavior requires a real device with a running app process to confirm consumeWidgetDocumentId() is called at the right moment and viewModel.openDocument() triggers navigation"
  - test: "Visual widget rendering on real Android device"
    expected: "Long-press home screen, select Notes widget, verify picker opens; pick a document; verify widget shows title in header and root bullets; completed bullets show strikethrough; dark/light mode switching updates widget colors"
    why_human: "Glance composable rendering cannot be verified without a running device or instrumented test"
---

# Phase 13: Widget Foundation Verification Report

**Phase Goal:** Users can place a Notes widget on their home screen, pick a document, and see its root bullets rendered with all display states correct
**Verified:** 2026-03-14T12:30:00Z
**Status:** human_needed (all automated checks pass)
**Re-verification:** Yes — after gap closure Plan 13-04

## Re-Verification Summary

Previous status: gaps_found (9/12, 2026-03-14T11:39:56Z)

| Gap | Previous Status | Current Status |
|-----|----------------|----------------|
| documentId wired through WidgetUiState.Content | failed | CLOSED |
| MainActivity handling OPEN_DOCUMENT_ID intent extra | failed | CLOSED |
| NotesWidgetReceiverTest.onDeleted unit test | partial | CLOSED |

Regressions: none detected.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can find and select a Notes widget in the Android widget picker | VERIFIED | NotesWidgetReceiver registered in AndroidManifest.xml with APPWIDGET_UPDATE intent-filter; notes_widget_info.xml has 4x2 target cells |
| 2 | Widget remembers which document it is showing after device reboot | VERIFIED | WidgetStateStore uses preferencesDataStore (persists across process death/reboot) with Tink AES256-GCM encryption; save/get/clear API fully implemented |
| 3 | Widget adapts its colors to system dark/light mode | VERIFIED | NotesWidgetColorScheme wraps LightColorScheme+DarkColorScheme into ColorProviders; GlanceTheme(colors = NotesWidgetColorScheme.colors) called in provideContent |
| 4 | Removing a widget cleans up its stored data | VERIFIED | NotesWidgetReceiver.onDeleted iterates appWidgetIds, calls clearDocumentId via runBlocking; NotesWidgetReceiverTest has 2 tests including Robolectric onDeleted coverage |
| 5 | Document picker opens when user places widget | VERIFIED | WidgetConfigActivity registered in manifest with APPWIDGET_CONFIGURE filter; notes_widget_info.xml android:configure attribute set; RESULT_CANCELED set in onCreate before UI |
| 6 | Tapping a document confirms selection, saves ID, updates widget, closes | VERIFIED | WidgetConfigViewModel.selectDocument saves to WidgetStateStore then emits documentSelectedEvent; Activity collects event and calls updateAll + setResult(RESULT_OK) + finish() |
| 7 | Back press in picker discards placement (RESULT_CANCELED) | VERIFIED | setResult(RESULT_CANCELED, ...) is the first statement after appWidgetId extraction in onCreate |
| 8 | Widget header shows document title left-aligned with [+] placeholder | VERIFIED | HeaderRow renders Text with defaultWeight() + "+" button; document title from WidgetUiState.Content.documentTitle; ellipsis via maxLines=1 |
| 9 | Root-level bullets shown as scrollable flat list with bullet dot + text | VERIFIED | ContentView uses LazyColumn + BulletRow; BulletRow renders 6dp dot + spacer + text |
| 10 | Completed bullets show strikethrough and reduced-opacity color | VERIFIED | BulletRow uses TextDecoration.LineThrough + Color(0xFF9CA3AF) for isComplete=true bullets |
| 11 | Tapping document title opens Notes app to that document | VERIFIED | WidgetUiState.Content.documentId field present; fetchWidgetData passes docId to Content; ContentView forwards state.documentId to HeaderRow; MainActivity.handleWidgetIntent reads OPEN_DOCUMENT_ID; MainScreen LaunchedEffect calls viewModel.openDocument |
| 12 | Tapping a bullet row opens Notes app to the document | VERIFIED | ContentView forwards state.documentId to each BulletRow (no hardcoded empty string); BulletRow puts documentId in OPEN_DOCUMENT_ID intent extra |

**Score:** 12/12 truths verified

---

## Required Artifacts

### Plan 01 Artifacts

| Artifact | Status | Notes |
|----------|--------|-------|
| `android/app/src/main/res/xml/notes_widget_info.xml` | VERIFIED | appwidget-provider with updatePeriodMillis=0, android:configure set |
| `android/app/src/main/java/.../widget/WidgetStateStore.kt` | VERIFIED | save/get/clear with Tink encryption |
| `android/app/src/main/java/.../widget/NotesWidget.kt` | VERIFIED | fetchWidgetData now passes documentId = docId into Content constructor (line 138) |
| `android/app/src/main/java/.../widget/NotesWidgetReceiver.kt` | VERIFIED | GlanceAppWidgetReceiver with onDeleted cleanup |
| `android/app/src/main/java/.../widget/WidgetUiState.kt` | VERIFIED | Content now carries val documentId: String as first field (line 36) |
| `android/app/src/main/java/.../widget/WidgetEntryPoint.kt` | VERIFIED | @EntryPoint exposing BulletRepository, DocumentRepository, TokenStore, DataStoreCookieJar |

### Plan 02 Artifacts

| Artifact | Status | Notes |
|----------|--------|-------|
| `android/app/src/main/java/.../widget/config/WidgetConfigActivity.kt` | VERIFIED | Full activity with RESULT_CANCELED, login form, document list |
| `android/app/src/main/java/.../widget/config/WidgetConfigViewModel.kt` | VERIFIED | Auth check on init, document loading, selectDocument |
| `android/app/src/test/.../widget/config/WidgetConfigViewModelTest.kt` | VERIFIED | 8 unit tests covering state transitions |

### Plan 03 Artifacts

| Artifact | Status | Notes |
|----------|--------|-------|
| `android/app/src/main/java/.../widget/WidgetContent.kt` | VERIFIED | ContentView passes state.documentId (not "") to HeaderRow and BulletRow (lines 101, 104) |
| `android/app/src/main/java/.../widget/RetryActionCallback.kt` | VERIFIED | ActionCallback calling NotesWidget().update() |
| `android/app/src/main/java/.../widget/ReconfigureActionCallback.kt` | VERIFIED | ActionCallback launching WidgetConfigActivity with appWidgetId |

### Plan 04 Artifacts (Gap Closure)

| Artifact | Expected | Status | Notes |
|----------|----------|--------|-------|
| `android/app/src/main/java/.../widget/WidgetUiState.kt` | val documentId: String in Content | VERIFIED | Line 36: `val documentId: String` present before documentTitle |
| `android/app/src/main/java/.../widget/WidgetContent.kt` | state.documentId forwarded | VERIFIED | Lines 101 and 104: both HeaderRow and BulletRow receive state.documentId |
| `android/app/src/main/java/com/gmaingret/notes/MainActivity.kt` | OPEN_DOCUMENT_ID handling | VERIFIED | Import at line 18; handleWidgetIntent reads it at line 83; called from onCreate (line 54) and onNewIntent (line 79) |
| `android/app/src/main/java/.../presentation/main/MainScreen.kt` | consumeWidgetDocumentId LaunchedEffect | VERIFIED | Lines 130-135: LocalContext cast + LaunchedEffect(Unit) + viewModel.openDocument |
| `android/app/src/test/.../widget/NotesWidgetReceiverTest.kt` | onDeleted clearDocumentId test | VERIFIED | Lines 54-69: Robolectric test with coVerify for IDs 1, 2, 3 |

---

## Key Link Verification

| From | To | Via | Status | Detail |
|------|----|-----|--------|--------|
| NotesWidget.fetchWidgetData | WidgetUiState.Content | documentId = docId constructor param | VERIFIED | Line 138 of NotesWidget.kt: `documentId = docId` |
| WidgetContent.ContentView | HeaderRow and BulletRow | state.documentId forwarded | VERIFIED | Lines 101 and 104 of WidgetContent.kt: `documentId = state.documentId` |
| MainActivity.onCreate | pendingWidgetDocId | handleWidgetIntent reads OPEN_DOCUMENT_ID | VERIFIED | Line 54 calls handleWidgetIntent(intent); line 83 reads getStringExtra(OPEN_DOCUMENT_ID) |
| MainActivity.onNewIntent | pendingWidgetDocId | handleWidgetIntent called | VERIFIED | Line 79: `handleWidgetIntent(intent)` in onNewIntent |
| MainScreen | viewModel.openDocument | consumeWidgetDocumentId via LocalContext | VERIFIED | Lines 130-135: activity cast + LaunchedEffect + openDocument call |
| AndroidManifest.xml | NotesWidgetReceiver | APPWIDGET_UPDATE intent-filter | VERIFIED (previous) | Confirmed in initial verification |
| notes_widget_info.xml | WidgetConfigActivity | android:configure attribute | VERIFIED (previous) | Confirmed in initial verification |
| WidgetConfigActivity | WidgetStateStore + NotesWidget | saveDocumentId + updateAll on selection | VERIFIED (previous) | Confirmed in initial verification |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SETUP-01 | Plan 01 | User can add the Notes widget to the Android home screen | SATISFIED | NotesWidgetReceiver + manifest registration |
| SETUP-02 | Plan 02 | User is presented with a document picker when adding the widget | SATISFIED | WidgetConfigActivity with full auth gate and document list |
| DISP-01 | Plans 03+04 | Widget shows the document title in a header row with tap navigation | SATISFIED | HeaderRow renders title; tap intent carries real documentId; MainActivity+MainScreen handle navigation |
| DISP-02 | Plan 03 | Widget shows root-level bullets as a scrollable flat list | SATISFIED | LazyColumn of BulletRow items; fetchWidgetData filters parentId==null |
| DISP-03 | Plan 03 | Widget shows an empty state when the document has no bullets | SATISFIED | EmptyContent() shows "No bullets yet" |
| DISP-04 | Plan 01 | Widget shows loading and error states appropriately | SATISFIED | LoadingContent, ErrorContent, DocumentNotFoundContent, SessionExpiredContent all present |
| DISP-05 | Plan 01 | Widget uses Material 3 theming consistent with the app | SATISFIED | GlanceTheme(colors = NotesWidgetColorScheme.colors) wrapping app's color schemes |

All 7 required IDs satisfied. No orphaned requirements.

---

## Anti-Patterns Found

No blockers or warnings in the Plan 04 modified files.

| File | Pattern | Severity | Status |
|------|---------|----------|--------|
| WidgetContent.kt | `documentId = ""` (previous) | Was Blocker | RESOLVED — replaced with state.documentId |
| WidgetUiState.kt | documentId missing from Content | Was Blocker | RESOLVED — val documentId: String added |
| MainActivity.kt | No OPEN_DOCUMENT_ID handling | Was Blocker | RESOLVED — handleWidgetIntent + onNewIntent |
| NotesWidgetReceiverTest.kt | onDeleted untested | Was Warning | RESOLVED — Robolectric test added |

---

## Human Verification Required

### 1. Widget Tap Navigation on Device

**Test:** Install APK via `adb install -r`. Long-press home screen, add Notes widget, pick a document. Once widget is displayed, tap either the document title or a bullet row.
**Expected:** Notes app opens (or comes to foreground if already running) and navigates directly to the configured document — not the root document list. The document's bullets should be visible immediately.
**Why human:** LaunchedEffect(Unit) runs once when MainScreen enters composition. The interaction between Activity launch flags (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK), onNewIntent delivery, and the LaunchedEffect execution timing requires a real Android device to confirm the navigation fires correctly in all scenarios (fresh launch, app backgrounded, app in foreground).

### 2. Widget Visual Rendering on Device

**Test:** Build APK (`./gradlew assembleDebug`), install via `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Long-press home screen, add Notes widget, pick a document with several bullets (including at least one marked complete). Verify widget renders.
**Expected:** Document title in header; root bullets as flat list with bullet dot; completed bullets show strikethrough in gray; loading skeleton shows while data fetches; dark/light mode toggle updates colors.
**Why human:** Glance composable rendering requires a real device or instrumented Espresso test.

---

## Gaps Summary

No gaps. All three previously identified blockers were resolved by Plan 04:

1. **documentId wired end-to-end** — WidgetUiState.Content.documentId added, fetchWidgetData passes the real ID, ContentView forwards it to HeaderRow and BulletRow. No hardcoded empty strings remain in WidgetContent.kt.

2. **MainActivity deep link handling** — handleWidgetIntent() reads OPEN_DOCUMENT_ID in both onCreate and onNewIntent. MainScreen consumes it via consumeWidgetDocumentId() and calls viewModel.openDocument().

3. **onDeleted unit test** — NotesWidgetReceiverTest now has a Robolectric test verifying clearDocumentId is called for each widget ID in the array (IDs 1, 2, 3 individually verified with coVerify).

Two items remain for human verification: the runtime tap navigation behavior and visual widget rendering on a physical device.

---

_Verified: 2026-03-14T12:30:00Z_
_Verifier: Claude (gsd-verifier)_
