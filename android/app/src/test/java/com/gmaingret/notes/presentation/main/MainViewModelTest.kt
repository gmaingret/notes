package com.gmaingret.notes.presentation.main

import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.usecase.CreateDocumentUseCase
import com.gmaingret.notes.domain.usecase.DeleteDocumentUseCase
import com.gmaingret.notes.domain.usecase.GetDocumentsUseCase
import com.gmaingret.notes.domain.usecase.LogoutUseCase
import com.gmaingret.notes.domain.usecase.OpenDocumentUseCase
import com.gmaingret.notes.domain.usecase.RenameDocumentUseCase
import com.gmaingret.notes.domain.usecase.ReorderDocumentUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getDocumentsUseCase: GetDocumentsUseCase
    private lateinit var createDocumentUseCase: CreateDocumentUseCase
    private lateinit var renameDocumentUseCase: RenameDocumentUseCase
    private lateinit var deleteDocumentUseCase: DeleteDocumentUseCase
    private lateinit var reorderDocumentUseCase: ReorderDocumentUseCase
    private lateinit var openDocumentUseCase: OpenDocumentUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var tokenStore: TokenStore

    private lateinit var viewModel: MainViewModel

    private val doc1 = Document(id = "doc-1", title = "First", position = 1.0)
    private val doc2 = Document(id = "doc-2", title = "Second", position = 2.0)
    private val doc3 = Document(id = "doc-3", title = "Third", position = 3.0)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getDocumentsUseCase = mockk()
        createDocumentUseCase = mockk()
        renameDocumentUseCase = mockk()
        deleteDocumentUseCase = mockk()
        reorderDocumentUseCase = mockk()
        openDocumentUseCase = mockk()
        logoutUseCase = mockk()
        tokenStore = mockk()

        // Default: no email
        coEvery { tokenStore.getUserEmail() } returns ""
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel = MainViewModel(
        logoutUseCase = logoutUseCase,
        tokenStore = tokenStore,
        getDocumentsUseCase = getDocumentsUseCase,
        createDocumentUseCase = createDocumentUseCase,
        renameDocumentUseCase = renameDocumentUseCase,
        deleteDocumentUseCase = deleteDocumentUseCase,
        reorderDocumentUseCase = reorderDocumentUseCase,
        openDocumentUseCase = openDocumentUseCase
    )

    // -----------------------------------------------------------------------
    // Init / Cold-start tests
    // -----------------------------------------------------------------------

    @Test
    fun `init loads documents and auto-opens last doc`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2))
        coEvery { openDocumentUseCase.getLastDocId() } returns "doc-2"
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MainUiState.Success)
        val success = state as MainUiState.Success
        assertEquals("doc-2", success.openDocumentId)
        assertEquals(listOf(doc1, doc2), success.documents)
    }

    @Test
    fun `init with no lastDocId opens first document`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2))
        coEvery { openDocumentUseCase.getLastDocId() } returns null
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value as MainUiState.Success
        assertEquals("doc-1", state.openDocumentId)
    }

    @Test
    fun `init with lastDocId not in list opens first document`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2))
        coEvery { openDocumentUseCase.getLastDocId() } returns "doc-deleted"
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value as MainUiState.Success
        assertEquals("doc-1", state.openDocumentId)
    }

    @Test
    fun `init with empty list transitions to Empty state`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(emptyList())
        coEvery { openDocumentUseCase.getLastDocId() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is MainUiState.Empty)
    }

    @Test
    fun `init with API failure transitions to Error state`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.failure(RuntimeException("Network error"))
        coEvery { openDocumentUseCase.getLastDocId() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MainUiState.Error)
    }

    // -----------------------------------------------------------------------
    // createDocument
    // -----------------------------------------------------------------------

    @Test
    fun `createDocument adds doc to list and sets inlineEditingDocId`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1))
        coEvery { openDocumentUseCase.getLastDocId() } returns null
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        val newDoc = Document(id = "doc-new", title = "Untitled", position = 2.0)
        coEvery { createDocumentUseCase("Untitled") } returns Result.success(newDoc)

        vm.createDocument()
        advanceUntilIdle()

        val state = vm.uiState.value as MainUiState.Success
        assertTrue(state.documents.any { it.id == "doc-new" })
        assertEquals("doc-new", state.inlineEditingDocId)
    }

    // -----------------------------------------------------------------------
    // deleteDocument
    // -----------------------------------------------------------------------

    @Test
    fun `deleteDocument removes doc and auto-opens next`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2, doc3))
        coEvery { openDocumentUseCase.getLastDocId() } returns "doc-1"
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        // doc-1 is open, delete it — should auto-open doc-2 (next)
        coEvery { deleteDocumentUseCase("doc-1") } returns Result.success(Unit)

        vm.deleteDocument("doc-1")
        advanceUntilIdle()

        val state = vm.uiState.value as MainUiState.Success
        assertEquals(2, state.documents.size)
        assertTrue(state.documents.none { it.id == "doc-1" })
        assertEquals("doc-2", state.openDocumentId)
    }

    @Test
    fun `deleteDocument on last doc auto-opens previous`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2, doc3))
        coEvery { openDocumentUseCase.getLastDocId() } returns "doc-3"
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        // doc-3 is last and open, delete it — should auto-open doc-2 (previous)
        coEvery { deleteDocumentUseCase("doc-3") } returns Result.success(Unit)

        vm.deleteDocument("doc-3")
        advanceUntilIdle()

        val state = vm.uiState.value as MainUiState.Success
        assertEquals("doc-2", state.openDocumentId)
    }

    @Test
    fun `deleteDocument on last remaining doc transitions to Empty`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1))
        coEvery { openDocumentUseCase.getLastDocId() } returns "doc-1"
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { deleteDocumentUseCase("doc-1") } returns Result.success(Unit)

        vm.deleteDocument("doc-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is MainUiState.Empty)
    }

    // -----------------------------------------------------------------------
    // moveDocumentLocally / commitReorder
    // -----------------------------------------------------------------------

    @Test
    fun `moveDocumentLocally reorders list in memory`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2, doc3))
        coEvery { openDocumentUseCase.getLastDocId() } returns null
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        // Move doc3 from index 2 to index 0
        vm.moveDocumentLocally(2, 0)

        val state = vm.uiState.value as MainUiState.Success
        assertEquals("doc-3", state.documents[0].id)
        assertEquals("doc-1", state.documents[1].id)
        assertEquals("doc-2", state.documents[2].id)
    }

    @Test
    fun `commitReorder calls use case with correct afterId`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2, doc3))
        coEvery { openDocumentUseCase.getLastDocId() } returns null
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        // doc2 is at index 1, doc1 is at index 0 (before doc2)
        coEvery { reorderDocumentUseCase("doc-2", "doc-1") } returns Result.success(doc2)

        vm.commitReorder("doc-2")
        advanceUntilIdle()

        coVerify(exactly = 1) { reorderDocumentUseCase("doc-2", "doc-1") }
    }

    @Test
    fun `commitReorder with doc at first position uses null afterId`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2, doc3))
        coEvery { openDocumentUseCase.getLastDocId() } returns null
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        // doc1 is at index 0, afterId should be null
        coEvery { reorderDocumentUseCase("doc-1", null) } returns Result.success(doc1)

        vm.commitReorder("doc-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { reorderDocumentUseCase("doc-1", null) }
    }

    @Test
    fun `commitReorder failure reverts list and emits snackbar`() = runTest {
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2, doc3))
        coEvery { openDocumentUseCase.getLastDocId() } returns null
        coEvery { openDocumentUseCase(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        // Move doc3 to top optimistically
        vm.moveDocumentLocally(2, 0)

        val stateAfterMove = vm.uiState.value as MainUiState.Success
        assertEquals("doc-3", stateAfterMove.documents[0].id)

        // Collect snackbar BEFORE triggering the reorder failure
        val snackbarMessages = mutableListOf<String>()
        val collectJob = launch {
            vm.snackbarMessage.collect { snackbarMessages.add(it) }
        }

        // Reorder fails — reload original list
        coEvery { reorderDocumentUseCase("doc-3", null) } returns Result.failure(RuntimeException("Server error"))
        coEvery { getDocumentsUseCase() } returns Result.success(listOf(doc1, doc2, doc3))

        vm.commitReorder("doc-3")
        advanceUntilIdle()

        collectJob.cancel()

        // List should be reverted (reloaded from API)
        val stateAfterRevert = vm.uiState.value as MainUiState.Success
        assertEquals("doc-1", stateAfterRevert.documents[0].id)

        // Snackbar should have been emitted
        assertTrue(snackbarMessages.isNotEmpty())
        assertTrue(snackbarMessages.first().isNotEmpty())
    }
}
