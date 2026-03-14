package com.gmaingret.notes.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.WorkManager
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
     * Cancels the periodic WorkManager sync job and clears all WidgetStateStore data
     * (document IDs, bullet cache, display state). Uses runBlocking because
     * BroadcastReceiver.onReceive() runs on the main thread and cannot be suspended.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Cancel the periodic background sync — no widget means no need to keep syncing
        WorkManager.getInstance(context).cancelUniqueWork("widget_sync")
        // Clear all persisted data (doc IDs, bullet cache, display state)
        val store = WidgetStateStore.getInstance(context)
        runBlocking {
            store.clearAll()
        }
    }
}
