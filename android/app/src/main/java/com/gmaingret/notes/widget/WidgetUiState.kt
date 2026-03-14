package com.gmaingret.notes.widget

/**
 * Represents a single bullet item as rendered by the widget.
 *
 * Only contains the fields needed for widget display — full Bullet domain
 * model is not carried into the Glance rendering context.
 */
data class WidgetBullet(
    val id: String,
    val content: String,
    val isComplete: Boolean
)

/**
 * All possible display states for the Notes widget.
 *
 * Transitions:
 *   App placed widget → NotConfigured (no document selected yet)
 *   User picks document → Loading → Content | Empty | Error | DocumentNotFound | SessionExpired
 *   WorkManager refresh → Loading → same states above
 */
sealed interface WidgetUiState {

    /** Initial state: widget placed but no document selected yet. */
    data object NotConfigured : WidgetUiState

    /** Fetching document data — show shimmer placeholder rows. */
    data object Loading : WidgetUiState

    /** Document selected and fetched, but contains no root-level bullets. */
    data object Empty : WidgetUiState

    /** Successfully loaded with at least one root bullet. */
    data class Content(
        val documentTitle: String,
        val bullets: List<WidgetBullet>
    ) : WidgetUiState

    /** Network or server error (not 401/404). */
    data class Error(val message: String) : WidgetUiState

    /** The previously selected document no longer exists on the server. */
    data object DocumentNotFound : WidgetUiState

    /** Auth token expired; user must re-authenticate. */
    data object SessionExpired : WidgetUiState
}
