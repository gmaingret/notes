package com.gmaingret.notes.presentation.bookmarks

import com.gmaingret.notes.domain.model.Bookmark
import com.gmaingret.notes.domain.usecase.GetBookmarksUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var getBookmarksUseCase: GetBookmarksUseCase
    private lateinit var viewModel: BookmarksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getBookmarksUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleBookmark = Bookmark(
        bulletId = "b1",
        content = "Important note",
        documentId = "d1",
        documentTitle = "My Document"
    )

    @Test
    fun `init triggers loadBookmarks`() = runTest {
        coEvery { getBookmarksUseCase() } returns Result.success(listOf(sampleBookmark))

        viewModel = BookmarksViewModel(getBookmarksUseCase)
        advanceUntilIdle()

        coVerify(exactly = 1) { getBookmarksUseCase() }
    }

    @Test
    fun `successful load with bookmarks sets Success state`() = runTest {
        coEvery { getBookmarksUseCase() } returns Result.success(listOf(sampleBookmark))

        viewModel = BookmarksViewModel(getBookmarksUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is BookmarksUiState.Success)
        assertEquals(listOf(sampleBookmark), (state as BookmarksUiState.Success).bookmarks)
    }

    @Test
    fun `successful load with empty list sets Empty state`() = runTest {
        coEvery { getBookmarksUseCase() } returns Result.success(emptyList())

        viewModel = BookmarksViewModel(getBookmarksUseCase)
        advanceUntilIdle()

        assertEquals(BookmarksUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `load failure sets Error state`() = runTest {
        coEvery { getBookmarksUseCase() } returns Result.failure(Exception("Network error"))

        viewModel = BookmarksViewModel(getBookmarksUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is BookmarksUiState.Error)
        assertEquals("Network error", (state as BookmarksUiState.Error).message)
    }

    @Test
    fun `explicit loadBookmarks call refreshes state`() = runTest {
        coEvery { getBookmarksUseCase() } returnsMany listOf(
            Result.success(emptyList()),
            Result.success(listOf(sampleBookmark))
        )

        viewModel = BookmarksViewModel(getBookmarksUseCase)
        advanceUntilIdle()

        assertEquals(BookmarksUiState.Empty, viewModel.uiState.value)

        viewModel.loadBookmarks()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is BookmarksUiState.Success)
    }
}
