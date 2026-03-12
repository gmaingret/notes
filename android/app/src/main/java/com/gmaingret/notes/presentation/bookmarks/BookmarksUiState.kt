package com.gmaingret.notes.presentation.bookmarks

import com.gmaingret.notes.domain.model.Bookmark

/**
 * UI state for the Bookmarks screen.
 *
 * - [Loading]: initial state while bookmarks are being fetched
 * - [Success]: bookmarks loaded (at least one)
 * - [Empty]: fetch succeeded but user has no bookmarks yet
 * - [Error]: API or network failure with human-readable message
 */
sealed interface BookmarksUiState {
    data object Loading : BookmarksUiState
    data class Success(val bookmarks: List<Bookmark>) : BookmarksUiState
    data object Empty : BookmarksUiState
    data class Error(val message: String) : BookmarksUiState
}
