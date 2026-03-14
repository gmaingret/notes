package com.gmaingret.notes.widget.config

import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.model.User
import com.gmaingret.notes.domain.repository.AuthRepository
import com.gmaingret.notes.domain.repository.DocumentRepository
import com.gmaingret.notes.widget.WidgetStateStore
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WidgetConfigViewModel.
 *
 * Uses MockK for all dependencies. No Android context required — pure Kotlin unit tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var documentRepository: DocumentRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var tokenStore: TokenStore
    private lateinit var widgetStateStore: WidgetStateStore

    private val doc1 = Document(id = "doc-1", title = "First Doc", position = 1.0)
    private val doc2 = Document(id = "doc-2", title = "Second Doc", position = 2.0)
    private val testUser = User(id = "user-1", email = "test@example.com")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        documentRepository = mockk()
        authRepository = mockk()
        tokenStore = mockk()
        widgetStateStore = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = WidgetConfigViewModel(
        documentRepository = documentRepository,
        authRepository = authRepository,
        tokenStore = tokenStore,
        widgetStateStore = widgetStateStore
    )

    // -------------------------------------------------------------------------
    // Init / auth check
    // -------------------------------------------------------------------------

    @Test
    fun `when token exists and checkAuth succeeds, state becomes DocumentsLoaded`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "valid-token"
        coEvery { authRepository.refresh() } returns Result.success("new-token")
        coEvery { documentRepository.getDocuments() } returns Result.success(listOf(doc1, doc2))

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected DocumentsLoaded, got $state", state is ConfigUiState.DocumentsLoaded)
        val loaded = state as ConfigUiState.DocumentsLoaded
        assertEquals(listOf(doc1, doc2), loaded.documents)
    }

    @Test
    fun `when no token, state is NeedsLogin`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected NeedsLogin, got $state", state is ConfigUiState.NeedsLogin)
    }

    @Test
    fun `when token exists but checkAuth fails, state is NeedsLogin`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "expired-token"
        coEvery { authRepository.refresh() } returns Result.failure(RuntimeException("401"))

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected NeedsLogin, got $state", state is ConfigUiState.NeedsLogin)
    }

    // -------------------------------------------------------------------------
    // Document loading errors
    // -------------------------------------------------------------------------

    @Test
    fun `when getDocuments fails, state is Error`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "valid-token"
        coEvery { authRepository.refresh() } returns Result.success("new-token")
        coEvery { documentRepository.getDocuments() } returns Result.failure(RuntimeException("Network error"))

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is ConfigUiState.Error)
        val error = state as ConfigUiState.Error
        assertNotNull(error.message)
    }

    // -------------------------------------------------------------------------
    // Login flow
    // -------------------------------------------------------------------------

    @Test
    fun `after login succeeds, documents are loaded`() = runTest {
        // Start unauthenticated
        coEvery { tokenStore.getAccessToken() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue("Expected NeedsLogin initially", vm.uiState.value is ConfigUiState.NeedsLogin)

        // Login
        coEvery { authRepository.login("test@example.com", "password") } returns Result.success(testUser)
        coEvery { documentRepository.getDocuments() } returns Result.success(listOf(doc1, doc2))

        vm.login("test@example.com", "password")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected DocumentsLoaded after login, got $state", state is ConfigUiState.DocumentsLoaded)
        val loaded = state as ConfigUiState.DocumentsLoaded
        assertEquals(listOf(doc1, doc2), loaded.documents)
    }

    @Test
    fun `when login fails, state is Error`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { authRepository.login("bad@email.com", "wrong") } returns Result.failure(RuntimeException("401"))

        vm.login("bad@email.com", "wrong")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error after login failure, got $state", state is ConfigUiState.Error)
    }

    // -------------------------------------------------------------------------
    // selectDocument
    // -------------------------------------------------------------------------

    @Test
    fun `selectDocument calls widgetStateStore saveDocumentId with correct args`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "valid-token"
        coEvery { authRepository.refresh() } returns Result.success("new-token")
        coEvery { documentRepository.getDocuments() } returns Result.success(listOf(doc1))

        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { widgetStateStore.saveDocumentId(42, "doc-1") } returns Unit

        vm.selectDocument(appWidgetId = 42, docId = "doc-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { widgetStateStore.saveDocumentId(42, "doc-1") }
    }

    @Test
    fun `selectDocument emits DocumentSelected event`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "valid-token"
        coEvery { authRepository.refresh() } returns Result.success("new-token")
        coEvery { documentRepository.getDocuments() } returns Result.success(listOf(doc1))

        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { widgetStateStore.saveDocumentId(any(), any()) } returns Unit

        val events = mutableListOf<Unit>()
        val collectJob = launch {
            vm.documentSelectedEvent.collect { events.add(it) }
        }

        vm.selectDocument(appWidgetId = 1, docId = "doc-1")
        advanceUntilIdle()

        collectJob.cancel()

        assertTrue("Expected DocumentSelected event", events.isNotEmpty())
    }
}
