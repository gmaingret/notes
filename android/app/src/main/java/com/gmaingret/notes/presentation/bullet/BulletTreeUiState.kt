package com.gmaingret.notes.presentation.bullet

import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.FlatBullet

/**
 * UI state for the bullet tree editor screen.
 *
 * - [Loading]: initial state shown while bullets are being fetched (skeleton shimmer)
 * - [Success]: bullets loaded, flat list computed, optional focus tracked
 * - [Error]: API or network failure with human-readable message
 */
sealed interface BulletTreeUiState {
    data object Loading : BulletTreeUiState

    data class Success(
        val bullets: List<Bullet>,          // raw server bullets (for optimistic updates)
        val flatList: List<FlatBullet>,      // flattened for LazyColumn rendering
        val focusedBulletId: String? = null,
        val focusCursorEnd: Boolean = false, // true = place cursor at end (after backspace merge)
        val scrollToFocused: Boolean = false // true = programmatic focus (Enter/backspace), scroll needed
    ) : BulletTreeUiState

    data class Error(val message: String) : BulletTreeUiState
}
