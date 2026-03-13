package com.gmaingret.notes.presentation.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.domain.usecase.GetTagCountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Tags browser screen.
 *
 * Loads all tag/mention/date counts on demand and groups them by chipType.
 * The three groups are ordered canonically: tag, mention, date.
 */
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val getTagCountsUseCase: GetTagCountsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<TagsUiState>(TagsUiState.Loading)
    val uiState: StateFlow<TagsUiState> = _uiState.asStateFlow()

    fun loadTags() {
        viewModelScope.launch {
            _uiState.value = TagsUiState.Loading
            getTagCountsUseCase().fold(
                onSuccess = { tags ->
                    if (tags.isEmpty()) {
                        _uiState.value = TagsUiState.Empty
                    } else {
                        // Group by chipType, preserving server sort (count DESC within each group)
                        val grouped = tags.groupBy { it.chipType }
                        _uiState.value = TagsUiState.Success(grouped)
                    }
                },
                onFailure = { e ->
                    _uiState.value = TagsUiState.Error(e.message ?: "Failed to load tags")
                }
            )
        }
    }
}
