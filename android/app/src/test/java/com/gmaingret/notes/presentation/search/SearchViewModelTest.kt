package com.gmaingret.notes.presentation.search

import com.gmaingret.notes.domain.model.SearchResult
import com.gmaingret.notes.domain.usecase.SearchBulletsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var searchBulletsUseCase: SearchBulletsUseCase
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        searchBulletsUseCase = mockk()
        viewModel = SearchViewModel(searchBulletsUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleResult = SearchResult(
        bulletId = "b1",
        content = "Sample bullet content",
        documentId = "d1",
        documentTitle = "Doc One"
    )

    @Test
    fun `initial state is Idle`() {
        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `blank query sets state to Empty without API call`() = runTest {
        viewModel.onQueryChange("")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty, viewModel.uiState.value)
        coVerify(exactly = 0) { searchBulletsUseCase(any()) }
    }

    @Test
    fun `whitespace-only query sets state to Empty without API call`() = runTest {
        viewModel.onQueryChange("   ")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty, viewModel.uiState.value)
        coVerify(exactly = 0) { searchBulletsUseCase(any()) }
    }

    @Test
    fun `query does NOT fire API call before 300ms debounce`() = runTest {
        coEvery { searchBulletsUseCase(any()) } returns Result.success(listOf(sampleResult))

        viewModel.onQueryChange("test")
        advanceTimeBy(200) // less than 300ms debounce — do NOT advanceUntilIdle here

        coVerify(exactly = 0) { searchBulletsUseCase(any()) }
    }

    @Test
    fun `query fires API call after 300ms debounce`() = runTest {
        coEvery { searchBulletsUseCase("test") } returns Result.success(listOf(sampleResult))

        viewModel.onQueryChange("test")
        advanceTimeBy(400)
        advanceUntilIdle()

        coVerify(exactly = 1) { searchBulletsUseCase("test") }
    }

    @Test
    fun `successful search groups results by documentTitle`() = runTest {
        val result2 = SearchResult(
            bulletId = "b2",
            content = "Another bullet",
            documentId = "d2",
            documentTitle = "Doc Two"
        )
        coEvery { searchBulletsUseCase("test") } returns Result.success(listOf(sampleResult, result2))

        val states = mutableListOf<SearchUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }

        viewModel.onQueryChange("test")
        advanceTimeBy(400)
        advanceUntilIdle()

        job.cancel()

        val successState = states.filterIsInstance<SearchUiState.Success>().last()
        assertEquals(2, successState.results.size)
        assertTrue(successState.results.containsKey("Doc One"))
        assertTrue(successState.results.containsKey("Doc Two"))
    }

    @Test
    fun `API failure sets state to Error`() = runTest {
        coEvery { searchBulletsUseCase("fail") } returns Result.failure(Exception("Search failed"))

        viewModel.onQueryChange("fail")
        advanceTimeBy(400)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Error)
        assertEquals("Search failed", (state as SearchUiState.Error).message)
    }

    @Test
    fun `reset sets state back to Idle`() = runTest {
        coEvery { searchBulletsUseCase("test") } returns Result.success(listOf(sampleResult))

        viewModel.onQueryChange("test")
        advanceTimeBy(400)
        advanceUntilIdle()

        viewModel.reset()

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `empty results from API sets state to Empty`() = runTest {
        coEvery { searchBulletsUseCase("noresults") } returns Result.success(emptyList())

        viewModel.onQueryChange("noresults")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty, viewModel.uiState.value)
    }
}
