package com.gmaingret.notes.presentation.search

import com.gmaingret.notes.domain.model.SearchResult

/**
 * UI state for the search screen/mode.
 *
 * - [Idle]: no search active (search bar not open)
 * - [Loading]: API call in flight after debounce
 * - [Success]: results grouped by document title
 * - [Empty]: blank query OR query returned no results
 * - [Error]: API or network failure
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: Map<String, List<SearchResult>>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}
