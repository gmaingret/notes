package com.gmaingret.notes.presentation.main

import com.gmaingret.notes.domain.model.Document

/**
 * UI state for the main screen document list.
 *
 * - [Loading]: initial state shown while documents are being fetched (skeleton/shimmer)
 * - [Success]: documents loaded, one is open, optional inline rename in progress
 * - [Error]: API or network failure with human-readable message
 * - [Empty]: fetch succeeded but user has no documents yet
 */
sealed interface MainUiState {
    data object Loading : MainUiState

    data class Success(
        val documents: List<Document>,
        val openDocumentId: String?,
        val inlineEditingDocId: String? = null,
        /** When true, renders BookmarksScreen instead of BulletTreeScreen. */
        val showBookmarks: Boolean = false,
        /** When non-null, BulletTreeScreen should scroll to this bullet ID after loading. */
        val pendingScrollToBulletId: String? = null
    ) : MainUiState

    data class Error(val message: String) : MainUiState

    data object Empty : MainUiState
}
