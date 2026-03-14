package com.gmaingret.notes.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.runBlocking

/**
 * BroadcastReceiver entry point for the Notes home screen widget.
 *
 * Registered in AndroidManifest.xml with the APPWIDGET_UPDATE intent filter.
 * The Glance framework calls onUpdate() automatically — we override only
 * onDeleted() to clean up persisted data when the user removes a widget.
 */
class NotesWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget = NotesWidget()

    /**
     * Called when the user removes one or more widget instances from the home screen.
     *
     * Clears each widget's document ID from WidgetStateStore to prevent stale entries
     * from accumulating. Uses runBlocking because BroadcastReceiver.onReceive() runs
     * on the main thread and cannot be suspended.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStateStore.getInstance(context)
        runBlocking {
            for (id in appWidgetIds) {
                store.clearDocumentId(id)
            }
        }
    }
}
