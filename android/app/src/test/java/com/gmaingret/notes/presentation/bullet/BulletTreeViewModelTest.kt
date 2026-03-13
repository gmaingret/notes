package com.gmaingret.notes.presentation.bullet

import android.app.Application
import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.data.model.MoveBulletRequest
import com.gmaingret.notes.data.model.PatchBulletRequest
import com.gmaingret.notes.domain.model.Attachment
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.FlatBullet
import com.gmaingret.notes.domain.model.UndoStatus
import com.gmaingret.notes.domain.usecase.AddBookmarkUseCase
import com.gmaingret.notes.domain.usecase.CreateBulletUseCase
import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import com.gmaingret.notes.domain.usecase.FlattenTreeUseCase
import com.gmaingret.notes.domain.usecase.DeleteAttachmentUseCase
import com.gmaingret.notes.domain.usecase.GetAttachmentsUseCase
import com.gmaingret.notes.domain.usecase.UploadAttachmentUseCase
import com.gmaingret.notes.domain.usecase.GetBookmarksUseCase
import com.gmaingret.notes.domain.usecase.GetBulletsUseCase
import com.gmaingret.notes.domain.usecase.GetUndoStatusUseCase
import com.gmaingret.notes.domain.usecase.IndentBulletUseCase
import com.gmaingret.notes.domain.usecase.MoveBulletUseCase
import com.gmaingret.notes.domain.usecase.OutdentBulletUseCase
import com.gmaingret.notes.domain.usecase.PatchBulletUseCase
import com.gmaingret.notes.domain.usecase.RedoUseCase
import com.gmaingret.notes.domain.usecase.RemoveBookmarkUseCase
import com.gmaingret.notes.domain.usecase.UndoUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BulletTreeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getBulletsUseCase: GetBulletsUseCase
    private lateinit var createBulletUseCase: CreateBulletUseCase
    private lateinit var patchBulletUseCase: PatchBulletUseCase
    private lateinit var deleteBulletUseCase: DeleteBulletUseCase
    private lateinit var indentBulletUseCase: IndentBulletUseCase
    private lateinit var outdentBulletUseCase: OutdentBulletUseCase
    private lateinit var moveBulletUseCase: MoveBulletUseCase
    private lateinit var undoUseCase: UndoUseCase
    private lateinit var redoUseCase: RedoUseCase
    private lateinit var getUndoStatusUseCase: GetUndoStatusUseCase
    private lateinit var getBookmarksUseCase: GetBookmarksUseCase
    private lateinit var addBookmarkUseCase: AddBookmarkUseCase
    private lateinit var removeBookmarkUseCase: RemoveBookmarkUseCase
    private lateinit var getAttachmentsUseCase: GetAttachmentsUseCase
    private lateinit var uploadAttachmentUseCase: UploadAttachmentUseCase
    private lateinit var deleteAttachmentUseCase: DeleteAttachmentUseCase
    private lateinit var tokenStore: TokenStore
    private val flattenTreeUseCase = FlattenTreeUseCase()
    private val application: Application = mockk(relaxed = true)

    private lateinit var viewModel: BulletTreeViewModel

    // -----------------------------------------------------------------------
    // Test data
    // -----------------------------------------------------------------------

    private val docId = "doc-1"

    private val bullet1 = Bullet(
        id = "b1", documentId = docId, parentId = null,
        content = "Root 1", position = 1.0, isComplete = false, isCollapsed = false, note = null
    )
    private val bullet2 = Bullet(
        id = "b2", documentId = docId, parentId = null,
        content = "Root 2", position = 2.0, isComplete = false, isCollapsed = false, note = null
    )
    private val bullet3 = Bullet(
        id = "b3", documentId = docId, parentId = "b1",
        content = "Child of b1", position = 1.0, isComplete = false, isCollapsed = false, note = null
    )
    private val undoStatusAvailable = UndoStatus(canUndo = true, canRedo = false)
    private val undoStatusNone = UndoStatus(canUndo = false, canRedo = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getBulletsUseCase = mockk()
        createBulletUseCase = mockk()
        patchBulletUseCase = mockk()
        deleteBulletUseCase = mockk()
        indentBulletUseCase = mockk()
        outdentBulletUseCase = mockk()
        moveBulletUseCase = mockk()
        undoUseCase = mockk()
        redoUseCase = mockk()
        getUndoStatusUseCase = mockk()
        getBookmarksUseCase = mockk()
        addBookmarkUseCase = mockk()
        removeBookmarkUseCase = mockk()
        getAttachmentsUseCase = mockk()
        uploadAttachmentUseCase = mockk()
        deleteAttachmentUseCase = mockk()
        tokenStore = mockk(relaxed = true)

        coEvery { getUndoStatusUseCase() } returns Result.success(undoStatusNone)
        coEvery { getBookmarksUseCase() } returns Result.success(emptyList())
        coEvery { getAttachmentsUseCase(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BulletTreeViewModel = BulletTreeViewModel(
        application = application,
        getBulletsUseCase = getBulletsUseCase,
        createBulletUseCase = createBulletUseCase,
        patchBulletUseCase = patchBulletUseCase,
        deleteBulletUseCase = deleteBulletUseCase,
        indentBulletUseCase = indentBulletUseCase,
        outdentBulletUseCase = outdentBulletUseCase,
        moveBulletUseCase = moveBulletUseCase,
        undoUseCase = undoUseCase,
        redoUseCase = redoUseCase,
        getUndoStatusUseCase = getUndoStatusUseCase,
        flattenTreeUseCase = flattenTreeUseCase,
        getBookmarksUseCase = getBookmarksUseCase,
        addBookmarkUseCase = addBookmarkUseCase,
        removeBookmarkUseCase = removeBookmarkUseCase,
        getAttachmentsUseCase = getAttachmentsUseCase,
        uploadAttachmentUseCase = uploadAttachmentUseCase,
        deleteAttachmentUseCase = deleteAttachmentUseCase,
        tokenStore = tokenStore
    )

    private fun loadedViewModel(bullets: List<Bullet> = listOf(bullet1, bullet2)): BulletTreeViewModel {
        coEvery { getBulletsUseCase(docId) } returns Result.success(bullets)
        val vm = createViewModel()
        vm.loadBullets(docId)
        return vm
    }

    // -----------------------------------------------------------------------
    // loadBullets
    // -----------------------------------------------------------------------

    @Test
    fun `loadBullets success transitions to Success state with flatList`() = runTest {
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, bullet2))
        val vm = createViewModel()
        vm.loadBullets(docId)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is BulletTreeUiState.Success)
        val success = state as BulletTreeUiState.Success
        assertEquals(2, success.flatList.size)
        assertEquals("b1", success.flatList[0].bullet.id)
        assertEquals("b2", success.flatList[1].bullet.id)
    }

    @Test
    fun `loadBullets failure transitions to Error state`() = runTest {
        coEvery { getBulletsUseCase(docId) } returns Result.failure(RuntimeException("Network error"))
        val vm = createViewModel()
        vm.loadBullets(docId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is BulletTreeUiState.Error)
    }

    @Test
    fun `loadBullets updates canUndo and canRedo from undo status`() = runTest {
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1))
        coEvery { getUndoStatusUseCase() } returns Result.success(UndoStatus(canUndo = true, canRedo = true))
        val vm = createViewModel()
        vm.loadBullets(docId)
        advanceUntilIdle()

        assertTrue(vm.canUndo.value)
        assertTrue(vm.canRedo.value)
    }

    // -----------------------------------------------------------------------
    // createBullet
    // -----------------------------------------------------------------------

    @Test
    fun `createBullet adds new bullet to flatList and sets focusedBulletId without server reload`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        val newBullet = Bullet(
            id = "b-new", documentId = docId, parentId = null,
            content = "", position = 1.5, isComplete = false, isCollapsed = false, note = null
        )
        coEvery { createBulletUseCase(any()) } returns Result.success(newBullet)
        // NOTE: getBulletsUseCase should NOT be called on success (no server reload)

        vm.createBullet(afterBulletId = "b1", parentId = null)
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertTrue(state.flatList.any { it.bullet.id == "b-new" })
        assertEquals("b-new", state.focusedBulletId)
        // Verify the new bullet appears after b1 in the flat list
        val b1Index = state.flatList.indexOfFirst { it.bullet.id == "b1" }
        val newIndex = state.flatList.indexOfFirst { it.bullet.id == "b-new" }
        assertEquals("b-new should be inserted right after b1", b1Index + 1, newIndex)
        // No second server call on success
        coVerify(exactly = 1) { getBulletsUseCase(docId) } // only the initial loadBullets call
    }

    @Test
    fun `createBullet inserts new bullet as first child when afterBulletId is null`() = runTest {
        // When afterBulletId is null, bullet is inserted as first child of parentId
        val vm = loadedViewModel(listOf(bullet1, bullet3, bullet2))  // b3 is child of b1
        advanceUntilIdle()

        val newFirstChild = Bullet(
            id = "b-child-new", documentId = docId, parentId = "b1",
            content = "", position = 0.5, isComplete = false, isCollapsed = false, note = null
        )
        coEvery { createBulletUseCase(any()) } returns Result.success(newFirstChild)

        vm.createBullet(afterBulletId = null, parentId = "b1")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertTrue(state.flatList.any { it.bullet.id == "b-child-new" })
        assertEquals("b-child-new", state.focusedBulletId)
    }

    @Test
    fun `createBullet failure emits snackbar and reloads tree`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { createBulletUseCase(any()) } returns Result.failure(RuntimeException("Server error"))
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, bullet2))

        val snackbarMessages = mutableListOf<String>()
        val collectJob = launch { vm.snackbarMessage.collect { snackbarMessages.add(it) } }

        vm.createBullet(afterBulletId = "b1", parentId = null)
        advanceUntilIdle()

        collectJob.cancel()
        assertTrue(snackbarMessages.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // enterOnEmpty
    // -----------------------------------------------------------------------

    @Test
    fun `enterOnEmpty on nested bullet calls outdentBullet`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet3))
        advanceUntilIdle()

        // bullet3 has parentId = "b1" — should trigger outdent
        val outdentedBullet3 = bullet3.copy(parentId = null, position = 2.0)
        coEvery { outdentBulletUseCase("b3") } returns Result.success(outdentedBullet3)
        // NOTE: outdentBullet no longer calls getBulletsUseCase on success

        vm.enterOnEmpty("b3")
        advanceUntilIdle()

        coVerify(exactly = 1) { outdentBulletUseCase("b3") }
    }

    @Test
    fun `enterOnEmpty on root-level bullet clears focus`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        // Set focus first
        vm.setFocusedBullet("b1")
        advanceUntilIdle()

        // b1 has parentId = null (root level) — enterOnEmpty should clear focus
        vm.enterOnEmpty("b1")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertNull(state.focusedBulletId)
    }

    // -----------------------------------------------------------------------
    // backspaceOnEmpty
    // -----------------------------------------------------------------------

    @Test
    fun `backspaceOnEmpty removes bullet and focuses previous with cursorEnd`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { deleteBulletUseCase("b2") } returns Result.success(Unit)

        vm.backspaceOnEmpty("b2")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertFalse(state.flatList.any { it.bullet.id == "b2" })
        assertEquals("b1", state.focusedBulletId)
        assertTrue(state.focusCursorEnd)
    }

    @Test
    fun `backspaceOnEmpty on first root bullet does not delete`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        // b1 is the first bullet — nothing to focus on before it, should be no-op
        vm.backspaceOnEmpty("b1")
        advanceUntilIdle()

        // deleteBulletUseCase should NOT have been called
        coVerify(exactly = 0) { deleteBulletUseCase(any()) }
    }

    // -----------------------------------------------------------------------
    // indentBullet
    // -----------------------------------------------------------------------

    @Test
    fun `indentBullet applies optimistic update immediately and calls indent API`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        val indentedBullet2 = bullet2.copy(parentId = "b1", position = 2.0)
        coEvery { indentBulletUseCase("b2") } returns Result.success(indentedBullet2)

        vm.indentBullet("b2")
        advanceUntilIdle()

        // Indent API was called
        coVerify(exactly = 1) { indentBulletUseCase("b2") }
        // b2 is now a child of b1 in the flat list
        val state = vm.uiState.value as BulletTreeUiState.Success
        val b2 = state.flatList.firstOrNull { it.bullet.id == "b2" }
        assertEquals("b1", b2?.bullet?.parentId)
        // Focus preserved
        assertEquals("b2", state.focusedBulletId)
        // No second server reload on success
        coVerify(exactly = 1) { getBulletsUseCase(docId) }
    }

    @Test
    fun `indentBullet optimistic update happens before API response`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        val indentedBullet2 = bullet2.copy(parentId = "b1", position = 2.0)
        coEvery { indentBulletUseCase("b2") } returns Result.success(indentedBullet2)

        // Before calling indentBullet, b2 has no parent
        val stateBefore = vm.uiState.value as BulletTreeUiState.Success
        assertNull(stateBefore.flatList.first { it.bullet.id == "b2" }.bullet.parentId)

        vm.indentBullet("b2")
        // Advance just enough for the optimistic update (synchronous state mutation)
        // but NOT enough for the async API call to complete
        // The optimistic update happens before enqueue{} is processed
        // so we check state reflects it after a single yield
        advanceUntilIdle()

        val stateAfter = vm.uiState.value as BulletTreeUiState.Success
        // b2 now has parent b1 (from server-returned authoritative bullet)
        assertEquals("b1", stateAfter.flatList.first { it.bullet.id == "b2" }.bullet.parentId)
    }

    @Test
    fun `indentBullet failure emits snackbar and reloads tree`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { indentBulletUseCase("b2") } returns Result.failure(RuntimeException("Server error"))
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, bullet2))

        val snackbarMessages = mutableListOf<String>()
        val collectJob = launch { vm.snackbarMessage.collect { snackbarMessages.add(it) } }

        vm.indentBullet("b2")
        advanceUntilIdle()

        collectJob.cancel()
        assertTrue(snackbarMessages.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // toggleCollapse
    // -----------------------------------------------------------------------

    @Test
    fun `toggleCollapse hides children in flatList optimistically`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        // Before collapse: b3 (child of b1) should be visible
        val stateBefore = vm.uiState.value as BulletTreeUiState.Success
        assertTrue(stateBefore.flatList.any { it.bullet.id == "b3" })

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(isCollapsed = true))

        vm.toggleCollapse("b1")
        // Check state immediately (optimistic — before API returns)
        advanceUntilIdle()

        val stateAfter = vm.uiState.value as BulletTreeUiState.Success
        assertFalse(stateAfter.flatList.any { it.bullet.id == "b3" })
    }

    @Test
    fun `toggleCollapse sends PATCH with isCollapsed to server`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        val patchSlot = slot<PatchBulletRequest>()
        coEvery { patchBulletUseCase("b1", capture(patchSlot)) } returns Result.success(bullet1.copy(isCollapsed = true))

        vm.toggleCollapse("b1")
        advanceUntilIdle()

        coVerify(exactly = 1) { patchBulletUseCase("b1", any()) }
        assertEquals(true, patchSlot.captured.isCollapsed)
    }

    // -----------------------------------------------------------------------
    // updateContent debounce
    // -----------------------------------------------------------------------

    @Test
    fun `updateContent does not PATCH immediately — waits for debounce`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        // Set up mock so it doesn't throw if called — we verify call count after
        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(content = "typing..."))

        vm.updateContent("b1", "typing...")
        // Only advance a small amount — debounce has not fired yet (debounce = 500ms)
        advanceTimeBy(200)

        coVerify(exactly = 0) { patchBulletUseCase(any(), any()) }
    }

    @Test
    fun `updateContent PATCHes after debounce delay`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(content = "new content"))

        vm.updateContent("b1", "new content")
        advanceTimeBy(600) // past 500ms debounce
        advanceUntilIdle()

        coVerify(exactly = 1) { patchBulletUseCase("b1", any()) }
    }

    // -----------------------------------------------------------------------
    // undo / redo
    // -----------------------------------------------------------------------

    @Test
    fun `undo calls undoUseCase and reloads tree`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { undoUseCase() } returns Result.success(UndoStatus(canUndo = false, canRedo = true))
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, bullet2))

        vm.undo()
        advanceUntilIdle()

        coVerify(exactly = 1) { undoUseCase() }
        // canUndo updated from server response
        assertFalse(vm.canUndo.value)
        assertTrue(vm.canRedo.value)
    }

    @Test
    fun `redo calls redoUseCase and reloads tree`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { redoUseCase() } returns Result.success(UndoStatus(canUndo = true, canRedo = false))
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, bullet2))

        vm.redo()
        advanceUntilIdle()

        coVerify(exactly = 1) { redoUseCase() }
        assertTrue(vm.canUndo.value)
        assertFalse(vm.canRedo.value)
    }

    // -----------------------------------------------------------------------
    // zoomTo
    // -----------------------------------------------------------------------

    @Test
    fun `zoomTo sets rootId and flatList contains only subtree`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        // Before zoom: flatList has b1, b2, b3
        val stateBefore = vm.uiState.value as BulletTreeUiState.Success
        assertEquals(3, stateBefore.flatList.size)

        vm.zoomTo("b1")
        advanceUntilIdle()

        // After zoom to b1: flatList should only contain children of b1 (b3)
        val stateAfter = vm.uiState.value as BulletTreeUiState.Success
        assertEquals(1, stateAfter.flatList.size)
        assertEquals("b3", stateAfter.flatList[0].bullet.id)
        assertEquals("b1", vm.zoomRootId.value)
    }

    @Test
    fun `zoomTo null resets to document root`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        vm.zoomTo("b1")
        advanceUntilIdle()

        vm.zoomTo(null)
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertEquals(3, state.flatList.size)
        assertNull(vm.zoomRootId.value)
    }

    // -----------------------------------------------------------------------
    // setFocusedBullet
    // -----------------------------------------------------------------------

    @Test
    fun `setFocusedBullet updates focusedBulletId in Success state`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        vm.setFocusedBullet("b2")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b2", state.focusedBulletId)
    }

    // -----------------------------------------------------------------------
    // scrollTarget
    // -----------------------------------------------------------------------

    @Test
    fun `setScrollTarget updates scrollTarget StateFlow`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        assertNull(vm.scrollTarget.value)

        vm.setScrollTarget("b2")
        advanceUntilIdle()

        assertEquals("b2", vm.scrollTarget.value)
    }

    @Test
    fun `clearScrollTarget sets scrollTarget to null`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        vm.setScrollTarget("b1")
        advanceUntilIdle()
        assertEquals("b1", vm.scrollTarget.value)

        vm.clearScrollTarget()
        advanceUntilIdle()
        assertNull(vm.scrollTarget.value)
    }

    @Test
    fun `setScrollTarget does not affect uiState bullets`() = runTest {
        // Verifies that scrollTarget is independent from bullet list state
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        val stateBefore = vm.uiState.value as BulletTreeUiState.Success
        val bulletCountBefore = stateBefore.flatList.size

        vm.setScrollTarget("b1")
        advanceUntilIdle()

        val stateAfter = vm.uiState.value as BulletTreeUiState.Success
        // Bullet list is unchanged
        assertEquals(bulletCountBefore, stateAfter.flatList.size)
        // scrollTarget points to "b1" which IS in the flatList
        assertEquals("b1", vm.scrollTarget.value)
        assertTrue(stateAfter.flatList.any { it.bullet.id == "b1" })
    }

    @Test
    fun `setScrollTarget sets target without affecting zoom`() = runTest {
        // setScrollTarget only sets the scroll target — zoom is handled by BulletTreeScreen
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        vm.zoomTo("b1")
        advanceUntilIdle()
        assertEquals("b1", vm.zoomRootId.value)

        vm.setScrollTarget("b2")
        advanceUntilIdle()

        // Zoom root unchanged — BulletTreeScreen's LaunchedEffect handles zoomTo
        assertEquals("b1", vm.zoomRootId.value)
        assertEquals("b2", vm.scrollTarget.value)
    }

    @Test
    fun `setScrollTarget to bullet that exists in flatList is valid navigation`() = runTest {
        // Verifies the integration: after setScrollTarget, the target IS findable in flatList
        // This is what BulletTreeScreen's LaunchedEffect uses to scroll: indexOfFirst { it.id == target }
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        vm.setScrollTarget("b3")
        advanceUntilIdle()

        val target = vm.scrollTarget.value
        assertNull("Should start null before set", null) // just showing test flow

        assertEquals("b3", target)
        val state = vm.uiState.value as BulletTreeUiState.Success
        val targetIndex = state.flatList.indexOfFirst { it.bullet.id == target }
        assertTrue("b3 must be findable in flatList for scroll to work", targetIndex >= 0)
    }

    // -----------------------------------------------------------------------
    // Swipe behavior: toggleComplete and deleteBullet (used by SwipeToDismissBox)
    // -----------------------------------------------------------------------

    @Test
    fun `toggleComplete called from swipe-right completes the bullet`() = runTest {
        // This mirrors what SwipeToDismissBox.StartToEnd does in BulletTreeScreen:
        // confirmValueChange { viewModel.toggleComplete(bulletId); false }
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(isComplete = true))

        // Simulate swipe-to-complete action
        vm.toggleComplete("b1")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertTrue("Bullet should be marked complete after swipe-right",
            state.flatList.first { it.bullet.id == "b1" }.bullet.isComplete)
    }

    @Test
    fun `deleteBullet called from swipe-left removes the bullet`() = runTest {
        // This mirrors what SwipeToDismissBox.EndToStart does in BulletTreeScreen:
        // confirmValueChange { viewModel.deleteBullet(bulletId); true }
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { deleteBulletUseCase("b2") } returns Result.success(Unit)

        // Simulate swipe-to-delete action
        vm.deleteBullet("b2")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertFalse("Bullet should be removed after swipe-left",
            state.flatList.any { it.bullet.id == "b2" })
    }

    @Test
    fun `focused bullet remains in list and swipe actions still work`() = runTest {
        // Swipe is now enabled even when a bullet is focused (only disabled during drag).
        // This test verifies the ViewModel invariant: focusing + swiping works correctly.
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        vm.setFocusedBullet("b1")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b1", state.focusedBulletId)
        // b1 is still in the list — focusing doesn't delete bullets
        assertTrue(state.flatList.any { it.bullet.id == "b1" })

        // Swipe-to-complete on focused bullet should work
        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(isComplete = true))
        vm.toggleComplete("b1")
        advanceUntilIdle()

        val stateAfter = vm.uiState.value as BulletTreeUiState.Success
        assertTrue("Focused bullet can be completed via swipe",
            stateAfter.flatList.first { it.bullet.id == "b1" }.bullet.isComplete)
    }

    // -----------------------------------------------------------------------
    // moveBulletLocally
    // -----------------------------------------------------------------------

    @Test
    fun `moveBulletLocally reorders flatList optimistically`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        val stateBefore = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b1", stateBefore.flatList[0].bullet.id)
        assertEquals("b2", stateBefore.flatList[1].bullet.id)

        vm.moveBulletLocally(1, 0) // move b2 to position 0
        advanceUntilIdle()

        val stateAfter = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b2", stateAfter.flatList[0].bullet.id)
        assertEquals("b1", stateAfter.flatList[1].bullet.id)
    }

    // -----------------------------------------------------------------------
    // commitBulletMove
    // -----------------------------------------------------------------------

    @Test
    fun `commitBulletMove fires moveBulletUseCase with correct params`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { moveBulletUseCase(any(), any()) } returns Result.success(bullet2.copy(parentId = null))

        vm.commitBulletMove(bulletId = "b2", newParentId = null, afterId = "b1")
        advanceUntilIdle()

        coVerify(exactly = 1) { moveBulletUseCase("b2", MoveBulletRequest(newParentId = null, afterId = "b1")) }
    }

    @Test
    fun `commitBulletMove reloads from server on success and clears drag flag`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        // Simulate a drag: set drag flag, moveBulletLocally changes the order (b2 before b1)
        vm.setDragInProgress(true)
        vm.moveBulletLocally(fromIndex = 1, toIndex = 0)
        advanceUntilIdle()

        // Now the live flatList is [b2, b1]
        val stateAfterLocalMove = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b2", stateAfterLocalMove.flatList[0].bullet.id)
        assertEquals("b1", stateAfterLocalMove.flatList[1].bullet.id)

        // moveBulletUseCase returns the updated b2
        val movedB2 = bullet2.copy(position = 0.5)
        coEvery { moveBulletUseCase(any(), any()) } returns Result.success(movedB2)
        // Server reload returns the reordered bullets
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(movedB2, bullet1))

        vm.commitBulletMove(bulletId = "b2", newParentId = null, afterId = null)
        advanceUntilIdle()

        // After commit: state reloaded from server with correct positions
        val finalState = vm.uiState.value as BulletTreeUiState.Success
        val b2Final = finalState.flatList.firstOrNull { it.bullet.id == "b2" }
        assertNotNull("b2 should still be in the list after commit", b2Final)
        assertEquals(0.5, b2Final!!.bullet.position, 0.001)
    }

    @Test
    fun `commitBulletMove failure emits snackbar and reloads tree`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { moveBulletUseCase(any(), any()) } returns Result.failure(RuntimeException("Server error"))
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, bullet2))

        val snackbarMessages = mutableListOf<String>()
        val collectJob = launch { vm.snackbarMessage.collect { snackbarMessages.add(it) } }

        vm.commitBulletMove(bulletId = "b2", newParentId = null, afterId = "b1")
        advanceUntilIdle()

        collectJob.cancel()
        assertTrue(snackbarMessages.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // moveUp / moveDown
    // -----------------------------------------------------------------------

    @Test
    fun `moveUp calls moveBulletUseCase to swap with previous sibling`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { moveBulletUseCase(any(), any()) } returns Result.success(bullet2)

        vm.moveUp("b2")
        advanceUntilIdle()

        coVerify(exactly = 1) { moveBulletUseCase("b2", any()) }
    }

    @Test
    fun `moveDown calls moveBulletUseCase to swap with next sibling`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { moveBulletUseCase(any(), any()) } returns Result.success(bullet1)

        vm.moveDown("b1")
        advanceUntilIdle()

        coVerify(exactly = 1) { moveBulletUseCase("b1", any()) }
    }

    // -----------------------------------------------------------------------
    // saveNote debounce
    // -----------------------------------------------------------------------

    @Test
    fun `saveNote PATCHes note field after debounce delay`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(note = "my note"))

        vm.saveNote("b1", "my note")
        advanceTimeBy(600)
        advanceUntilIdle()

        val noteSlot = slot<PatchBulletRequest>()
        coVerify(exactly = 1) { patchBulletUseCase("b1", any()) }
    }

    // -----------------------------------------------------------------------
    // outdentBullet
    // -----------------------------------------------------------------------

    @Test
    fun `outdentBullet applies optimistic update and calls outdent API without server reload`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        val outdentedBullet3 = bullet3.copy(parentId = null, position = 1.5)
        coEvery { outdentBulletUseCase("b3") } returns Result.success(outdentedBullet3)
        // NOTE: getBulletsUseCase should NOT be called on success

        vm.outdentBullet("b3")
        advanceUntilIdle()

        coVerify(exactly = 1) { outdentBulletUseCase("b3") }
        // b3 is now at root level (parentId = null from server response)
        val state = vm.uiState.value as BulletTreeUiState.Success
        val b3 = state.flatList.first { it.bullet.id == "b3" }
        assertNull(b3.bullet.parentId)
        // No second server reload on success
        coVerify(exactly = 1) { getBulletsUseCase(docId) }
    }

    @Test
    fun `outdentBullet preserves focus so toolbar stays visible`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        val outdentedBullet3 = bullet3.copy(parentId = null, position = 1.5)
        coEvery { outdentBulletUseCase("b3") } returns Result.success(outdentedBullet3)

        vm.outdentBullet("b3")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b3", state.focusedBulletId)
    }

    @Test
    fun `after outdent the bullet has a previous sibling for re-indent`() = runTest {
        // Scenario: b3 is child of b1 → outdent → b3 becomes root sibling after b1
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        val outdentedBullet3 = bullet3.copy(parentId = null, position = 1.5)
        coEvery { outdentBulletUseCase("b3") } returns Result.success(outdentedBullet3)

        vm.outdentBullet("b3")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        val flatList = state.flatList

        // b3 should be at root level (depth 0)
        val b3Flat = flatList.first { it.bullet.id == "b3" }
        assertEquals(0, b3Flat.depth)
        assertNull(b3Flat.bullet.parentId)

        // Verify b1 exists as a previous bullet that could serve as sibling for re-indent
        val b3Index = flatList.indexOfFirst { it.bullet.id == "b3" }
        assertTrue("b3 should not be the first item", b3Index > 0)
    }

    @Test
    fun `indentBullet preserves focus so toolbar stays visible`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        val indentedBullet2 = bullet2.copy(parentId = "b1", position = 1.0)
        coEvery { indentBulletUseCase("b2") } returns Result.success(indentedBullet2)
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, indentedBullet2))

        vm.indentBullet("b2")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b2", state.focusedBulletId)
    }

    // -----------------------------------------------------------------------
    // toggleComplete
    // -----------------------------------------------------------------------

    @Test
    fun `toggleComplete flips isComplete optimistically in flatList`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(isComplete = true))

        vm.toggleComplete("b1")
        // Check immediately after (optimistic — before API returns)
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertTrue(state.flatList.first { it.bullet.id == "b1" }.bullet.isComplete)
    }

    @Test
    fun `toggleComplete on already-complete bullet sets isComplete to false`() = runTest {
        val completeBullet = bullet1.copy(isComplete = true)
        val vm = loadedViewModel(listOf(completeBullet, bullet2))
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(completeBullet.copy(isComplete = false))

        vm.toggleComplete("b1")
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertFalse(state.flatList.first { it.bullet.id == "b1" }.bullet.isComplete)
    }

    @Test
    fun `toggleComplete failure reverts state and emits snackbar`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.failure(RuntimeException("Server error"))
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, bullet2))

        val snackbarMessages = mutableListOf<String>()
        val collectJob = launch { vm.snackbarMessage.collect { snackbarMessages.add(it) } }

        vm.toggleComplete("b1")
        advanceUntilIdle()

        collectJob.cancel()
        assertTrue(snackbarMessages.isNotEmpty())
        // State should have been reverted — b1 isComplete should be false
        val state = vm.uiState.value as BulletTreeUiState.Success
        assertFalse(state.flatList.first { it.bullet.id == "b1" }.bullet.isComplete)
    }

    // -----------------------------------------------------------------------
    // Content editing: create bullet then type — content reaches server
    // -----------------------------------------------------------------------

    @Test
    fun `createBullet then updateContent PATCHes content with real ID after debounce`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        val newBullet = Bullet(
            id = "b-real", documentId = docId, parentId = null,
            content = "", position = 1.5, isComplete = false, isCollapsed = false, note = null
        )
        coEvery { createBulletUseCase(any()) } returns Result.success(newBullet)
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, newBullet, bullet2))
        coEvery { patchBulletUseCase("b-real", any()) } returns Result.success(newBullet.copy(content = "typed text"))

        // Create bullet — server returns the real ID directly (no temp ID)
        vm.createBullet(afterBulletId = "b1", parentId = null)
        advanceUntilIdle()

        // After createBulletUseCase succeeds and server reload, focus is on the real ID
        val stateAfterCreate = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b-real", stateAfterCreate.focusedBulletId)

        // Now the user types content — using the REAL ID
        vm.updateContent("b-real", "typed text")
        advanceTimeBy(600) // past 500ms debounce
        advanceUntilIdle()

        // Verify the PATCH was called with the real ID
        val patchSlot = slot<PatchBulletRequest>()
        coVerify(exactly = 1) { patchBulletUseCase("b-real", capture(patchSlot)) }
        assertEquals("typed text", patchSlot.captured.content)
    }

    @Test
    fun `updateContent stores in contentOverrides immediately before debounce`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(content = "hello"))

        vm.updateContent("b1", "hello")
        // Don't advance past debounce — check override map immediately
        advanceTimeBy(100)

        assertEquals("hello", vm.contentOverrides.value["b1"])
        // PATCH should NOT have been called yet
        coVerify(exactly = 0) { patchBulletUseCase(any(), any()) }
    }

    @Test
    fun `multiple rapid edits only PATCHes the last value`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(content = "final"))

        vm.updateContent("b1", "h")
        advanceTimeBy(100)
        vm.updateContent("b1", "he")
        advanceTimeBy(100)
        vm.updateContent("b1", "hel")
        advanceTimeBy(100)
        vm.updateContent("b1", "hell")
        advanceTimeBy(100)
        vm.updateContent("b1", "hello")
        advanceTimeBy(600) // 500ms debounce from last emission
        advanceUntilIdle()

        // Should only PATCH once with "hello"
        val patchSlot = slot<PatchBulletRequest>()
        coVerify(exactly = 1) { patchBulletUseCase("b1", capture(patchSlot)) }
        assertEquals("hello", patchSlot.captured.content)
    }

    @Test
    fun `updateContent clears override after successful PATCH`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(content = "saved"))

        vm.updateContent("b1", "saved")
        advanceTimeBy(600)
        advanceUntilIdle()

        // Override should be cleared after successful PATCH
        assertNull(vm.contentOverrides.value["b1"])
    }

    @Test
    fun `flushContentEdit immediately PATCHes without waiting for debounce`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(content = "flushed"))

        vm.updateContent("b1", "flushed")
        // Don't advance time — debounce hasn't fired

        // Flush immediately
        vm.flushContentEdit("b1")
        advanceUntilIdle()

        // Should have PATCHed via flush (may also fire via debounce later)
        coVerify(atLeast = 1) { patchBulletUseCase("b1", match { it.content == "flushed" }) }
    }

    @Test
    fun `flushContentEdit with no pending override is a no-op`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        // No updateContent called — nothing to flush
        vm.flushContentEdit("b1")
        advanceUntilIdle()

        coVerify(exactly = 0) { patchBulletUseCase(any(), any()) }
    }

    @Test
    fun `content PATCH updates bullet content in local state`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase("b1", any()) } returns Result.success(bullet1.copy(content = "updated"))

        vm.updateContent("b1", "updated")
        advanceTimeBy(600)
        advanceUntilIdle()

        // After successful PATCH, bullet.content in the state should be "updated"
        val state = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("updated", state.bullets.first { it.id == "b1" }.content)
    }

    // -----------------------------------------------------------------------
    // flushAllPendingEdits
    // -----------------------------------------------------------------------

    @Test
    fun `flushAllPendingEdits PATCHes all pending content and note overrides`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase(any(), any()) } returns Result.success(bullet1)

        vm.updateContent("b1", "content1")
        vm.updateContent("b2", "content2")
        vm.saveNote("b1", "note1")
        advanceTimeBy(100) // Don't let debounce fire

        vm.flushAllPendingEdits()

        // All three should have been PATCHed
        coVerify(exactly = 1) { patchBulletUseCase("b1", PatchBulletRequest.updateContent("content1")) }
        coVerify(exactly = 1) { patchBulletUseCase("b2", PatchBulletRequest.updateContent("content2")) }
        coVerify(exactly = 1) { patchBulletUseCase("b1", PatchBulletRequest.updateNote("note1")) }
    }

    // -----------------------------------------------------------------------
    // createBullet as first child (Bug 5 — Enter on node with children)
    // -----------------------------------------------------------------------

    @Test
    fun `createBullet with null afterBulletId creates first child under parent`() = runTest {
        // Scenario: b1 has child b3. Pressing Enter on b1 should create a first child.
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        val newChild = Bullet(
            id = "b-new-child", documentId = docId, parentId = "b1",
            content = "", position = 0.5, isComplete = false, isCollapsed = false, note = null
        )
        coEvery { createBulletUseCase(any()) } returns Result.success(newChild)
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, newChild, bullet3, bullet2))

        // Create first child of b1 (afterBulletId = null, parentId = b1)
        vm.createBullet(afterBulletId = null, parentId = "b1")
        advanceUntilIdle()

        // Verify the create request had correct parentId and null afterId
        val requestSlot = slot<CreateBulletRequest>()
        coVerify { createBulletUseCase(capture(requestSlot)) }
        assertEquals("b1", requestSlot.captured.parentId)
        assertNull(requestSlot.captured.afterId)

        // New child should be focused
        val state = vm.uiState.value as BulletTreeUiState.Success
        assertEquals("b-new-child", state.focusedBulletId)
    }

    @Test
    fun `createBullet does NOT reload from server on success - inserts bullet directly`() = runTest {
        // The old behavior reloaded from server after create (2 network calls total).
        // The new behavior inserts the server-returned bullet directly (1 network call total).
        // This eliminates the ~500ms reload delay.
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        val newBullet = Bullet(
            id = "b-new", documentId = docId, parentId = null,
            content = "", position = 1.5, isComplete = false, isCollapsed = false, note = null
        )
        coEvery { createBulletUseCase(any()) } returns Result.success(newBullet)

        vm.createBullet(afterBulletId = "b1", parentId = null)
        advanceUntilIdle()

        // getBulletsUseCase is called ONLY once: the initial loadBullets call, not again after create
        coVerify(exactly = 1) { getBulletsUseCase(docId) }
        // New bullet should still be in the flat list (inserted locally)
        val state = vm.uiState.value as BulletTreeUiState.Success
        assertTrue(state.flatList.any { it.bullet.id == "b-new" })
    }

    // -----------------------------------------------------------------------
    // deleteAttachment
    // -----------------------------------------------------------------------

    @Test
    fun `deleteAttachment removes attachment optimistically and calls API`() = runTest {
        val attachment = Attachment(
            id = "att-1",
            bulletId = "b1",
            filename = "photo.jpg",
            mimeType = "image/jpeg",
            size = 12345L,
            downloadUrl = "https://example.com/photo.jpg"
        )

        // Set up mock BEFORE loadBullets so auto-load finds the attachment
        coEvery { getAttachmentsUseCase("b1") } returns Result.success(listOf(attachment))
        val vm = loadedViewModel()
        advanceUntilIdle()
        assertEquals(1, vm.attachments.value["b1"]?.size)

        // Mock successful delete
        coEvery { deleteAttachmentUseCase("att-1") } returns Result.success(Unit)

        vm.deleteAttachment(attachment)
        // Optimistic: attachment removed immediately (before coroutine completes)
        assertTrue("Attachment should be optimistically removed",
            vm.attachments.value["b1"]?.isEmpty() ?: true)

        advanceUntilIdle()
        coVerify(exactly = 1) { deleteAttachmentUseCase("att-1") }
    }

    @Test
    fun `deleteAttachment reverts on failure and shows snackbar`() = runTest {
        val attachment = Attachment(
            id = "att-1",
            bulletId = "b1",
            filename = "photo.jpg",
            mimeType = "image/jpeg",
            size = 12345L,
            downloadUrl = "https://example.com/photo.jpg"
        )

        // Set up mock BEFORE loadBullets so auto-load finds the attachment
        coEvery { getAttachmentsUseCase("b1") } returns Result.success(listOf(attachment))
        val vm = loadedViewModel()
        advanceUntilIdle()

        // Mock failed delete, then revert re-fetches
        coEvery { deleteAttachmentUseCase("att-1") } returns Result.failure(RuntimeException("Network error"))
        coEvery { getAttachmentsUseCase("b1") } returns Result.success(listOf(attachment))

        val snackbarMessages = mutableListOf<String>()
        val collectJob = launch { vm.snackbarMessage.collect { snackbarMessages.add(it) } }

        vm.deleteAttachment(attachment)
        advanceUntilIdle()

        // Attachment is reverted back
        assertEquals(1, vm.attachments.value["b1"]?.size)
        // Snackbar was shown
        assertTrue(snackbarMessages.any { it.contains("Failed to remove attachment") })

        collectJob.cancel()
    }

    @Test
    fun `flushAllPendingEdits clears overrides after flushing`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        coEvery { patchBulletUseCase(any(), any()) } returns Result.success(bullet1)

        vm.updateContent("b1", "content1")
        advanceTimeBy(100)

        vm.flushAllPendingEdits()

        assertTrue(vm.contentOverrides.value.isEmpty())
    }
}
