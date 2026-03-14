package com.gmaingret.notes.widget.sync

import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.NotesWidget
import com.gmaingret.notes.widget.WidgetEntryPoint
import com.gmaingret.notes.widget.WidgetStateStore
import com.gmaingret.notes.widget.WidgetUiState

/**
 * Testable helper for the widget refresh trigger logic extracted from BulletTreeViewModel.
 *
 * Determines whether the given document is the one pinned to the widget, and if so,
 * fetches fresh data and writes it to the WidgetStateStore cache.
 *
 * @param currentDocId The document currently open in the app.
 * @param widgetStateStore The WidgetStateStore to read the pinned doc ID from and write cache to.
 * @param entryPoint The Hilt entry point providing repository access.
 * @return true if the widget was refreshed, false if docId didn't match or no widget is configured.
 */
internal suspend fun refreshWidgetIfDocMatches(
    currentDocId: String,
    widgetStateStore: WidgetStateStore,
    entryPoint: WidgetEntryPoint
): Boolean {
    val pinnedDocId = widgetStateStore.getFirstDocumentId()
    if (pinnedDocId == null || pinnedDocId != currentDocId) return false

    val widget = NotesWidget()
    val uiState = widget.fetchWidgetData(entryPoint, currentDocId)

    when (uiState) {
        is WidgetUiState.Content -> {
            widgetStateStore.saveBullets(uiState.bullets)
            widgetStateStore.saveDisplayState(DisplayState.CONTENT)
        }
        is WidgetUiState.Empty -> {
            widgetStateStore.saveBullets(emptyList())
            widgetStateStore.saveDisplayState(DisplayState.EMPTY)
        }
        is WidgetUiState.SessionExpired -> widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
        is WidgetUiState.DocumentNotFound -> widgetStateStore.saveDisplayState(DisplayState.DOCUMENT_NOT_FOUND)
        is WidgetUiState.Error -> widgetStateStore.saveDisplayState(DisplayState.ERROR)
        else -> { /* Loading, NotConfigured — ignore */ }
    }

    return true
}
