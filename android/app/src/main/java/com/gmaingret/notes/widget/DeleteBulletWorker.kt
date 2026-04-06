package com.gmaingret.notes.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import retrofit2.HttpException

private const val TAG = "DeleteBulletWorker"

/**
 * WorkManager worker that performs the actual API delete for a bullet.
 *
 * Runs with proper lifecycle guarantees — unlike ActionCallback which executes
 * in a BroadcastReceiver-like context with strict time/network restrictions.
 *
 * On success: logs and finishes (optimistic state already committed).
 * On auth error: sets SESSION_EXPIRED and rolls back.
 * On other failure: rolls back to original bullets and returns RETRY
 *   (WorkManager will retry with exponential backoff).
 */
class DeleteBulletWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_BULLET_ID = "bullet_id"
        const val KEY_ORIGINAL_IDS = "original_ids"
        const val KEY_ORIGINAL_CONTENTS = "original_contents"
        const val KEY_ORIGINAL_COMPLETES = "original_completes"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val bulletId = inputData.getString(KEY_BULLET_ID)
            ?: return Result.failure()

        WidgetDebugLog.log(context, TAG, "doWork: deleting bullet $bulletId (attempt ${runAttemptCount + 1})")

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WidgetEntryPoint::class.java
        )
        val deleteBulletUseCase = entryPoint.deleteBulletUseCase()

        val result = deleteBulletUseCase.invoke(bulletId)

        return if (result.isSuccess) {
            WidgetDebugLog.log(context, TAG, "API delete SUCCESS for $bulletId")
            Result.success()
        } else {
            val ex = result.exceptionOrNull()
            WidgetDebugLog.error(context, TAG, "API delete FAILED for $bulletId", ex)

            val store = WidgetStateStore.getInstance(context)

            if (isAuthError(ex)) {
                WidgetDebugLog.warn(context, TAG, "auth error — setting SESSION_EXPIRED")
                rollback(context, store)
                store.saveDisplayState(DisplayState.SESSION_EXPIRED)
                NotesWidget.pushStateToGlance(context)
                Result.failure()
            } else if (runAttemptCount < 3) {
                // Retry up to 3 times — WorkManager handles backoff
                WidgetDebugLog.warn(context, TAG, "will retry (attempt ${runAttemptCount + 1})")
                Result.retry()
            } else {
                WidgetDebugLog.warn(context, TAG, "max retries reached — rolling back")
                rollback(context, store)
                NotesWidget.pushStateToGlance(context)
                Result.failure()
            }
        }
    }

    private suspend fun rollback(context: Context, store: WidgetStateStore) {
        val ids = inputData.getStringArray(KEY_ORIGINAL_IDS) ?: return
        val contents = inputData.getStringArray(KEY_ORIGINAL_CONTENTS) ?: return
        val completes = inputData.getBooleanArray(KEY_ORIGINAL_COMPLETES) ?: return

        val originalBullets = ids.mapIndexed { i, id ->
            WidgetBullet(id = id, content = contents[i], isComplete = completes[i])
        }
        store.saveBullets(originalBullets)
        store.saveDisplayState(if (originalBullets.isEmpty()) DisplayState.EMPTY else DisplayState.CONTENT)
        WidgetDebugLog.warn(context, TAG, "rolled back to ${originalBullets.size} bullets")
    }

    private fun isAuthError(e: Throwable?): Boolean {
        if (e == null) return false
        if (e is HttpException && e.code() == 401) return true
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("401") || msg.contains("unauthorized") || msg.contains("unauthenticated")
    }
}
