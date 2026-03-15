<original_task>
Fix mobile UI bugs on the Android Notes app. The user reported bugs across two rounds of feedback:

**Round 1 (initial):**
1. Increase font size, reduce margin between nodes
2. Search and tap on a result should zoom/navigate to that node
3. No button to add attachment
4. Can't indent a new node after pressing Enter
5. Select a node with children, press Enter — should create child at top, not sibling

**Round 2 (after first fixes):**
1. Font still too small — needs MORE increase
2. Search tap still doesn't navigate to the node
3. Attachment button just toggles show/hide, should let user ADD attachments
4. Outdent a node, then can't indent it back
5. Enter on node with children is glitchy
6. Tag panel results don't navigate to the node

**Round 3 (latest, after second fixes):**
1. Search tap STILL doesn't navigate (just closes search)
2. Swipe to complete/delete is extremely glitchy and doesn't work
3. Tap on image attachments should show in a lightbox
4. Enter on node with children creates at bottom then moves to top (should directly add at 1st position)
6. Tag panel results STILL don't navigate

**Critical user instruction:** "I will not test anything anymore. Write all tests required so you can test yourself. Don't come back until 100% sure issues are fixed." This feedback has been saved to memory at `C:\Users\gmain\.claude\projects\C--Users-gmain-Dev-Notes\memory\feedback_self_test.md`.
</original_task>

<work_completed>
## Files Modified

### 1. `android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletRow.kt`
- **Font size**: Changed ALL `fontSize` values from 14sp → 16sp → 18sp → **20.sp** (current state)
  - Lines affected: ~334 (edit mode TextField), ~356 (strikethrough), ~366 (empty placeholder), ~374 (markdown text), ~388 (FlowRow text segments)
  - Used `replace_all` to change all instances
- **Row min height**: Changed `defaultMinSize(minHeight = 48.dp)` → 40dp → **32.dp** (line ~200)
- **Row padding**: Changed top/bottom from 6.dp → 2.dp → **1.dp** (line ~202)

### 2. `android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeScreen.kt`
- **canIndent fix (Bug 4 — can't indent after outdent)**: Lines ~200-219
  - OLD: Only checked `flatList[focusedIndex - 1].bullet.parentId == fb.bullet.parentId`
  - NEW: Scans backwards through entire flatList with `for (i in (focusedIndex - 1) downTo 0)` to find ANY previous bullet with same parentId. Early exits when `candidate.depth < fb.depth`.
- **Search navigation (partial)**: Lines ~220-248
  - Added `viewModel.setFocusedBullet(pendingScrollToBulletId)` after `animateScrollToItem` — but this alone doesn't fix the root cause (see Attempted Approaches)
- **Enter on node with children**: Lines ~357-367
  - When `flatBullet.hasChildren && !flatBullet.bullet.isCollapsed`, creates first child: `createBullet(afterBulletId = null, parentId = flatBullet.bullet.id)`
- **Attachment toolbar**: Line ~579-581 — `onAttachment = { viewModel.requestAttachmentUpload(bulletId) }` (wired correctly by first debugger)

### 3. `android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModel.kt`
- **createBullet simplified** (CURRENT STATE — partially done):
  - REMOVED entire temp bullet mechanism (temp UUID, optimistic insert, content/note migration)
  - NOW: Just enqueues the server call, reloads from server on success, focuses new bullet
  - This eliminates the "creates at bottom then moves to top" visual glitch
- **Added scrollTarget StateFlow** (CURRENT STATE — added but NOT yet wired):
  ```kotlin
  private val _scrollTarget = MutableStateFlow<String?>(null)
  val scrollTarget: StateFlow<String?> = _scrollTarget.asStateFlow()
  fun setScrollTarget(bulletId: String) { _scrollTarget.value = bulletId }
  fun clearScrollTarget() { _scrollTarget.value = null }
  ```
  This was added after `_hideCompleted` declaration block.
- **indent/outdent**: Both now reload full tree from server after operation, preserving `focusedBulletId`

### 4. `android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletEditingToolbar.kt`
- Added 8th button (attachment/paperclip icon) with `onAttachment` callback (done by first debugger, verified correct)

### 5. `android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModelTest.kt`
- Added tests for outdent preserving focus
- Added test for "after outdent, bullet has previous sibling for re-indent"
- Added test for indent preserving focus
- Added test for createBullet with null afterBulletId (first child)
- Added test for createBullet reloading from server
- Updated existing tests to mock `getBulletsUseCase` for indent/outdent/create (since they now reload from server)
- **WARNING**: Tests were passing BEFORE the final `createBullet` simplification. The simplification removed temp bullet code, so tests like `createBullet migrates content override from temp ID to real ID` will FAIL and need to be removed or rewritten.

### 6. Memory files
- Created `feedback_self_test.md` — user wants Claude to self-test, never ask for manual verification
- Updated `MEMORY.md` with link to this feedback

## Builds & Installs
- Multiple APKs built and installed via `adb install -r`
- Tests run via `./gradlew testDebugUnitTest --tests "com.gmaingret.notes.presentation.bullet.BulletTreeViewModelTest"`
- Last successful test run was BEFORE the final createBullet simplification
</work_completed>

<work_remaining>
## HIGH PRIORITY — Must fix before delivering

### 1. Search/Tag Navigation (Bugs 1 & 6 from Round 3) — NOT FIXED
The `pendingScrollToBulletId` parameter passing through Crossfade doesn't work reliably. Need to:

**Step A**: Wire `scrollTarget` in BulletTreeScreen (collect from ViewModel):
```kotlin
// In BulletTreeScreen, inside the Success block, add:
val vmScrollTarget by viewModel.scrollTarget.collectAsState()
```
Then merge it with the existing `pendingScrollToBulletId` parameter in the LaunchedEffect:
```kotlin
val effectiveScrollTarget = pendingScrollToBulletId ?: vmScrollTarget
LaunchedEffect(effectiveScrollTarget, flatList.size) {
    if (effectiveScrollTarget != null) {
        val targetIndex = flatList.indexOfFirst { it.bullet.id == effectiveScrollTarget }
        if (targetIndex >= 0) {
            lazyListState.animateScrollToItem(targetIndex)
            viewModel.setFocusedBullet(effectiveScrollTarget)
            viewModel.clearScrollTarget()
            onClearPendingScroll()
        }
    }
}
```

**Step B**: Wire in MainScreen — get BulletTreeViewModel and call setScrollTarget:
In `MainScreen.kt`, add: `val bulletTreeViewModel: BulletTreeViewModel = hiltViewModel()`
Then in the search result onClick (line ~486):
```kotlin
onClick = {
    viewModel.navigateToBullet(result.documentId, result.bulletId)
    bulletTreeViewModel.setScrollTarget(result.bulletId)
    isSearchActive = false
    searchQuery = ""
    searchViewModel.reset()
}
```
Also in BookmarksScreen onClick (line ~362):
```kotlin
onBookmarkClick = { bookmark ->
    viewModel.navigateToBullet(bookmark.documentId, bookmark.bulletId)
    bulletTreeViewModel.setScrollTarget(bookmark.bulletId)
}
```

### 2. Swipe to Complete/Delete Glitchy (Bug 2 from Round 3) — NOT FIXED
The first debugger agent removed `animateItem()` from LazyColumn items to fix Enter glitchiness. This broke SwipeToDismissBox which needs item animations.

**Fix**: In `BulletTreeScreen.kt`, add `.animateItem()` back to the `Modifier` chain on the BulletRow or SwipeToDismissBox wrapper. The exact location is inside the `items(...)` block, on the `ReorderableItem` content. Try adding it to the SwipeToDismissBox or directly after `ReorderableItem`:
```kotlin
ReorderableItem(reorderableState, key = flatBullet.bullet.id) { isDragging ->
    // Add animateItem() to the outer modifier
    ...
}
```
Note: The previous animateItem() removal was to fix Enter-key glitchiness which is now fixed by removing the temp bullet mechanism. So animateItem() can be safely restored.

### 3. Update Unit Tests — MUST DO
The `createBullet` simplification removed the temp bullet mechanism. These tests will FAIL:
- `createBullet migrates content override from temp ID to real ID` — **DELETE** this test entirely (no more temp IDs)
- `createBullet adds new bullet to flatList and sets focusedBulletId` — Update: remove temp bullet assertions, just verify real bullet appears after server reload
- `createBullet then updateContent PATCHes content with real ID after debounce` — Simplify: no temp ID phase
- `createBullet failure emits snackbar and reloads tree` — Should still work but verify
- `createBullet reloads from server on success` — Should still work

### 4. Build, Test, Install
```bash
cd /c/Users/gmain/Dev/Notes/android
./gradlew testDebugUnitTest --tests "com.gmaingret.notes.presentation.bullet.BulletTreeViewModelTest"
./gradlew assembleDebug
adb install -r /c/Users/gmain/Dev/Notes/android/app/build/outputs/apk/debug/app-debug.apk
```

## MEDIUM PRIORITY

### 5. Image Lightbox (Bug 3 from Round 3) — NEW FEATURE
User wants to tap on image attachments and see them in a fullscreen lightbox. This requires:
- Detecting if an attachment is an image (check MIME type or file extension)
- Creating a fullscreen dialog/overlay composable with the image displayed large
- Using Coil (already in project — `SingletonImageLoader.Factory` on `NotesApplication` reuses auth OkHttpClient)
- Adding tap handler on image attachments in `AttachmentList` composable
- Look at existing `AttachmentList` composable for current rendering (likely in `BulletRow.kt` or a separate file)

### 6. Write MainViewModel Tests for navigateToBullet
Test that `navigateToBullet(documentId, bulletId)` correctly sets `openDocumentId`, `showBookmarks = false`, `showTags = false`, and `pendingScrollToBulletId`.
</work_remaining>

<attempted_approaches>
## Search/Tag Navigation — Multiple Failed Approaches

### Approach 1: pendingScrollToBulletId through Crossfade parameter (DOESN'T WORK)
- MainViewModel.navigateToBullet() sets `pendingScrollToBulletId` in MainUiState
- BulletTreeScreen receives it as a parameter through Crossfade content lambda
- LaunchedEffect keyed on `(pendingScrollToBulletId, flatList.size)` scrolls when target found
- **Why it fails**: Extensive analysis couldn't pinpoint the exact Crossfade recomposition issue. The code LOOKS correct — Compose state reads inside Crossfade lambdas should trigger recomposition. But the user confirms it doesn't work across 3 rounds. Possible causes:
  - Crossfade animation lifecycle interfering with LaunchedEffect scheduling
  - State batching causing the pendingScrollToBulletId to be set and cleared in the same frame
  - The LaunchedEffect being inside the `is BulletTreeUiState.Success` block and not firing during Loading→Success transitions correctly

### Approach 2: setFocusedBullet after scroll (PARTIAL — doesn't fix navigation)
- Added `viewModel.setFocusedBullet(pendingScrollToBulletId)` after `animateScrollToItem`
- This highlights the bullet but doesn't help if the scroll itself never fires

### Approach 3 (CURRENT — NOT YET WIRED): Direct ViewModel StateFlow
- Added `scrollTarget` StateFlow to BulletTreeViewModel
- Bypasses Crossfade parameter passing entirely
- MainScreen sets it directly, BulletTreeScreen reads from ViewModel
- **This is the approach to complete** (see Work Remaining)

## createBullet Temp Bullet Mechanism — Evolution

### Approach 1: Optimistic temp bullet insert (ORIGINAL)
- Insert temp bullet with `temp-UUID`, replace with real ID on server response
- **Problem**: LazyColumn key change (temp→real) causes visual glitch/flash

### Approach 2: Server reload after temp insert (TRIED)
- Still inserted temp bullet, but reloaded from server on success
- **Problem**: Same key-change issue — temp bullet removed, real bullet added

### Approach 3: No temp bullet at all (CURRENT)
- Wait for server response, then reload and focus
- **Trade-off**: ~100ms delay before new bullet appears (acceptable for local server)
- **Benefit**: No visual glitch, no key change, no content migration complexity

## Swipe Glitchiness
- First debugger removed `animateItem()` to fix Enter-key visual glitch
- This broke SwipeToDismissBox which needs item animation support
- Now that temp bullet is removed, `animateItem()` can be safely restored
</attempted_approaches>

<critical_context>
## Architecture
- **BulletTreeViewModel** is shared across all BulletTreeScreen compositions (hiltViewModel() scoped to Activity)
- **MainViewModel** manages document list, navigation state, and pendingScrollToBulletId
- **Crossfade** in MainScreen switches between "bookmarks", "tags", and "doc:ID" content
- **FlattenTreeUseCase** is pure Kotlin — groups bullets by parentId, sorts by position within each group, DFS traversal

## Server Behavior
- `afterId = null` in createBullet → server inserts BEFORE first sibling (position = first_sibling_position / 2)
- Server code: `server/src/services/bulletService.ts:53-55`
- Indent/outdent are server-side operations that return the updated bullet

## Key Design Decisions
- **No temp bullets**: Removed optimistic temp bullet insert. Trade-off: slight delay for bullet creation, but eliminates ALL visual glitches from key changes
- **canIndent scans backwards**: Must scan through flatList backwards (not just check index-1) because after outdent, intervening bullets may be at different depths
- **scrollTarget on ViewModel**: Bypasses Crossfade parameter passing for reliable navigation
- **Coil 3.1.0** (not 3.4.0) for image loading — 3.4.0 requires Kotlin 2.3+

## Testing Strategy
- User REFUSES to manually test. All verification must be through unit tests + code review
- Run tests: `./gradlew testDebugUnitTest --tests "com.gmaingret.notes.presentation.bullet.BulletTreeViewModelTest"`
- Build: `./gradlew assembleDebug`
- Install: `adb install -r /c/Users/gmain/Dev/Notes/android/app/build/outputs/apk/debug/app-debug.apk`
- The `java.util.UUID` import in BulletTreeViewModel.kt may now be unused (temp bullets removed) — check and remove if so

## Git State
- Branch: `phase-12/reactivity-and-polish`
- Many uncommitted changes across presentation layer files
- Do NOT commit until user confirms (per CLAUDE.md workflow)

## File Locations
- Android source: `android/app/src/main/java/com/gmaingret/notes/`
- Tests: `android/app/src/test/java/com/gmaingret/notes/`
- Server: `server/src/`
- Gradle build from: `cd /c/Users/gmain/Dev/Notes/android`
</critical_context>

<current_state>
## Deliverable Status

| Bug | Status | Notes |
|-----|--------|-------|
| Font size (20sp) | COMPLETE | All instances updated, verified |
| canIndent after outdent | COMPLETE | Scans backwards, tested |
| createBullet simplified (no temp) | CODE DONE, TESTS BROKEN | Need to update/remove tests that reference temp IDs |
| Search/tag navigation | IN PROGRESS | scrollTarget StateFlow added to VM but NOT wired in MainScreen/BulletTreeScreen |
| Swipe glitchy | NOT STARTED | Need to restore animateItem() |
| Image lightbox | NOT STARTED | New feature request |
| Enter creates first child | COMPLETE | Server confirms afterId=null → first position |
| Attachment upload button | COMPLETE | Full chain verified (toolbar → VM → file picker → upload) |

## What's in the code but NOT tested/built
- The `createBullet` simplification (removed temp bullet) — code is saved but tests haven't been updated and APK hasn't been rebuilt
- The `scrollTarget` StateFlow — added to ViewModel but not yet wired in UI

## Temporary State
- Last installed APK does NOT have the latest changes (createBullet simplification, scrollTarget)
- Unit tests will FAIL if run now due to createBullet changes removing temp bullet mechanism

## Open Questions
- Should `animateItem()` be added only to SwipeToDismissBox items, or to all LazyColumn items?
- For image lightbox: use Dialog composable or fullscreen Activity? (Dialog is simpler)
- The `import java.util.UUID` in BulletTreeViewModel.kt may now be unused — verify
</current_state>
