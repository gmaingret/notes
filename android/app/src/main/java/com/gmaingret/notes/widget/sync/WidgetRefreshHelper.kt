package com.gmaingret.notes.widget.sync

import android.content.Context
import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.NotesWidget
import com.gmaingret.notes.widget.WidgetDebugLog
import com.gmaingret.notes.widget.WidgetEntryPoint
import com.gmaingret.notes.widget.WidgetStateStore
import com.gmaingret.notes.widget.WidgetUiState

private const val TAG = "WidgetRefresh"

internal suspend fun refreshWidgetIfDocMatches(
    currentDocId: String,
    widgetStateStore: WidgetStateStore,
    entryPoint: WidgetEntryPoint,
    context: Context
): Boolean {
    val pinnedDocId = widgetStateStore.getFirstDocumentId()
    if (pinnedDocId == null || pinnedDocId != currentDocId) return false

    if (widgetStateStore.isMutationInFlight()) {
        WidgetDebugLog.log(context, TAG, "refreshWidgetIfDocMatches SKIPPED — mutation in flight")
        return false
    }

    WidgetDebugLog.log(context, TAG, "refreshWidgetIfDocMatches RUNNING for doc=$currentDocId")
    val widget = NotesWidget()
    val uiState = widget.fetchWidgetData(entryPoint, currentDocId)
    WidgetDebugLog.log(context, TAG, "fetchWidgetData returned ${uiState::class.simpleName}")

    when (uiState) {
        is WidgetUiState.Content -> {
            WidgetDebugLog.log(context, TAG, "writing ${uiState.bullets.size} bullets, ids=${uiState.bullets.map { it.id }}")
            widgetStateStore.saveBullets(uiState.bullets)
            widgetStateStore.saveDisplayState(DisplayState.CONTENT)
        }
        is WidgetUiState.Empty -> {
            WidgetDebugLog.log(context, TAG, "writing EMPTY (0 bullets)")
            widgetStateStore.saveBullets(emptyList())
            widgetStateStore.saveDisplayState(DisplayState.EMPTY)
        }
        is WidgetUiState.SessionExpired -> {
            WidgetDebugLog.warn(context, TAG, "SESSION_EXPIRED from fetchWidgetData")
            widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
        }
        is WidgetUiState.DocumentNotFound -> {
            WidgetDebugLog.warn(context, TAG, "DOCUMENT_NOT_FOUND from fetchWidgetData")
            widgetStateStore.saveDisplayState(DisplayState.DOCUMENT_NOT_FOUND)
        }
        is WidgetUiState.Error -> {
            WidgetDebugLog.error(context, TAG, "ERROR from fetchWidgetData: ${(uiState as? WidgetUiState.Error)}")
            widgetStateStore.saveDisplayState(DisplayState.ERROR)
        }
        else -> { /* Loading, NotConfigured — ignore */ }
    }

    WidgetDebugLog.log(context, TAG, "pushing state to Glance")
    NotesWidget.pushStateToGlance(context)

    return true
}
