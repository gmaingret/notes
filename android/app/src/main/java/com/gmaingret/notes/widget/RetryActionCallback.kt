package com.gmaingret.notes.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * ActionCallback that triggers a full widget re-render when the user taps
 * the "Couldn't load — tap to retry" error state.
 *
 * Calling [NotesWidget.update] re-invokes [NotesWidget.provideGlance], which
 * re-fetches data from the network and updates the widget state.
 */
class RetryActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        NotesWidget().update(context, glanceId)
    }
}
