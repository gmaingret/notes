package com.gmaingret.notes.presentation.tags

import com.gmaingret.notes.domain.model.TagCount

/**
 * UI state for the Tags browser screen.
 *
 * Tags are grouped into three chip types: tag, mention, date.
 * [grouped] maps chipType -> list of TagCount, ordered by count descending.
 */
sealed interface TagsUiState {
    data object Loading : TagsUiState
    data object Empty : TagsUiState
    data class Success(
        val grouped: Map<String, List<TagCount>>
    ) : TagsUiState
    data class Error(val message: String) : TagsUiState
}
