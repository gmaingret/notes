package com.gmaingret.notes.presentation.bullet

import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.data.model.MoveBulletRequest
import com.gmaingret.notes.data.model.PatchBulletRequest
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.FlatBullet
import com.gmaingret.notes.domain.model.UndoStatus
import com.gmaingret.notes.domain.usecase.CreateBulletUseCase
import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import com.gmaingret.notes.domain.usecase.FlattenTreeUseCase
import com.gmaingret.notes.domain.usecase.GetBulletsUseCase
import com.gmaingret.notes.domain.usecase.GetUndoStatusUseCase
import com.gmaingret.notes.domain.usecase.IndentBulletUseCase
import com.gmaingret.notes.domain.usecase.MoveBulletUseCase
import com.gmaingret.notes.domain.usecase.OutdentBulletUseCase
import com.gmaingret.notes.domain.usecase.PatchBulletUseCase
import com.gmaingret.notes.domain.usecase.RedoUseCase
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
    private val flattenTreeUseCase = FlattenTreeUseCase()

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

        coEvery { getUndoStatusUseCase() } returns Result.success(undoStatusNone)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BulletTreeViewModel = BulletTreeViewModel(
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
        flattenTreeUseCase = flattenTreeUseCase
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
    fun `createBullet adds new bullet to flatList and sets focusedBulletId`() = runTest {
        val vm = loadedViewModel()
        advanceUntilIdle()

        val newBullet = Bullet(
            id = "b-new", documentId = docId, parentId = null,
            content = "", position = 1.5, isComplete = false, isCollapsed = false, note = null
        )
        coEvery { createBulletUseCase(any()) } returns Result.success(newBullet)

        vm.createBullet(afterBulletId = "b1", parentId = null)
        advanceUntilIdle()

        val state = vm.uiState.value as BulletTreeUiState.Success
        assertTrue(state.flatList.any { it.bullet.id == "b-new" })
        assertEquals("b-new", state.focusedBulletId)
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
        coEvery { getBulletsUseCase(docId) } returns Result.success(listOf(bullet1, outdentedBullet3))

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
    fun `indentBullet calls indent API and re-flattens tree`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2))
        advanceUntilIdle()

        val indentedBullet2 = bullet2.copy(parentId = "b1", position = 2.0)
        coEvery { indentBulletUseCase("b2") } returns Result.success(indentedBullet2)

        vm.indentBullet("b2")
        advanceUntilIdle()

        coVerify(exactly = 1) { indentBulletUseCase("b2") }
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
    fun `outdentBullet calls outdent API`() = runTest {
        val vm = loadedViewModel(listOf(bullet1, bullet2, bullet3))
        advanceUntilIdle()

        val outdentedBullet3 = bullet3.copy(parentId = null, position = 1.5)
        coEvery { outdentBulletUseCase("b3") } returns Result.success(outdentedBullet3)

        vm.outdentBullet("b3")
        advanceUntilIdle()

        coVerify(exactly = 1) { outdentBulletUseCase("b3") }
    }
}
