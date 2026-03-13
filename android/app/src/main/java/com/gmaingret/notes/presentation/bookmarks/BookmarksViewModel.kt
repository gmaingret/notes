package com.gmaingret.notes.presentation.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.domain.usecase.GetBookmarksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Bookmarks screen.
 *
 * Loads all bookmarks on init and exposes them as [uiState].
 * Supports manual refresh via [loadBookmarks].
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val getBookmarksUseCase: GetBookmarksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookmarksUiState>(BookmarksUiState.Loading)
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        loadBookmarks()
    }

    fun loadBookmarks() {
        viewModelScope.launch {
            _uiState.value = BookmarksUiState.Loading
            getBookmarksUseCase().fold(
                onSuccess = { bookmarks ->
                    _uiState.value = if (bookmarks.isEmpty()) {
                        BookmarksUiState.Empty
                    } else {
                        BookmarksUiState.Success(bookmarks)
                    }
                },
                onFailure = { e ->
                    _uiState.value = BookmarksUiState.Error(e.message ?: "Failed to load bookmarks")
                }
            )
        }
    }
}
