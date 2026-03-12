package com.gmaingret.notes.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.domain.usecase.SearchBulletsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for inline search mode in MainScreen.
 *
 * Exposes [uiState] as a [StateFlow]. Queries are debounced 300ms via [queryFlow]
 * to avoid hammering the API on every keystroke.
 *
 * Usage:
 * - Call [onQueryChange] whenever the user types in the search field.
 * - Call [reset] when the search bar closes to return to [SearchUiState.Idle].
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchBulletsUseCase: SearchBulletsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Replay=0 so stale queries don't fire after the collector subscribes
    private val queryFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)

    init {
        viewModelScope.launch {
            queryFlow.debounce(300).collect { query ->
                if (query.isBlank()) {
                    _uiState.value = SearchUiState.Empty
                } else {
                    _uiState.value = SearchUiState.Loading
                    searchBulletsUseCase(query).fold(
                        onSuccess = { results ->
                            _uiState.value = if (results.isEmpty()) {
                                SearchUiState.Empty
                            } else {
                                SearchUiState.Success(results.groupBy { it.documentTitle })
                            }
                        },
                        onFailure = { e ->
                            _uiState.value = SearchUiState.Error(e.message ?: "Search failed")
                        }
                    )
                }
            }
        }
    }

    /** Called on every keystroke in the search field. Queued into debounce flow. */
    fun onQueryChange(query: String) {
        viewModelScope.launch { queryFlow.emit(query) }
    }

    /** Called when the search bar is dismissed. Resets state to [SearchUiState.Idle]. */
    fun reset() {
        _uiState.value = SearchUiState.Idle
    }
}
