package com.gmaingret.notes.widget

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

private const val TAG = "WidgetDelete"

class DeleteBulletActionCallback : ActionCallback {

    companion object {
        val BULLET_ID_PARAM = ActionParameters.Key<String>("bullet_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val bulletId = parameters[BULLET_ID_PARAM]
        WidgetDebugLog.log(context, TAG, "onAction fired — bulletId=$bulletId glanceId=$glanceId")

        if (bulletId == null) {
            WidgetDebugLog.warn(context, TAG, "bulletId is null, aborting")
            return
        }

        val store = WidgetStateStore.getInstance(context)

        // Optimistic UI update — remove bullet from widget immediately
        val originalBullets = store.getBullets()
        val filtered = originalBullets.filter { it.id != bulletId }
        val newDisplayState = if (filtered.isEmpty()) DisplayState.EMPTY else DisplayState.CONTENT
        WidgetDebugLog.log(context, TAG, "optimistic: original=${originalBullets.size} filtered=${filtered.size} newState=$newDisplayState")

        if (originalBullets.size == filtered.size) {
            WidgetDebugLog.warn(context, TAG, "bullet $bulletId NOT FOUND in cached list — ids: ${originalBullets.map { it.id }}")
        }

        // Persist optimistic state
        store.saveBullets(filtered)
        store.saveDisplayState(newDisplayState)

        try {
            NotesWidget.pushToGlanceDirect(context, newDisplayState, filtered)
            WidgetDebugLog.log(context, TAG, "pushToGlanceDirect OK")
        } catch (e: Exception) {
            WidgetDebugLog.error(context, TAG, "pushToGlanceDirect FAILED", e)
        }

        // Delegate the actual API call to a WorkManager worker.
        // ActionCallback runs in a BroadcastReceiver-like context with strict
        // time and network restrictions. WorkManager has proper lifecycle guarantees
        // and will retry with backoff if the network is temporarily unavailable.
        val workRequest = OneTimeWorkRequestBuilder<DeleteBulletWorker>()
            .setInputData(workDataOf(
                DeleteBulletWorker.KEY_BULLET_ID to bulletId,
                DeleteBulletWorker.KEY_ORIGINAL_IDS to originalBullets.map { it.id }.toTypedArray(),
                DeleteBulletWorker.KEY_ORIGINAL_CONTENTS to originalBullets.map { it.content }.toTypedArray(),
                DeleteBulletWorker.KEY_ORIGINAL_COMPLETES to originalBullets.map { it.isComplete }.toBooleanArray()
            ))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        WidgetDebugLog.log(context, TAG, "enqueued DeleteBulletWorker for $bulletId")
    }
}
