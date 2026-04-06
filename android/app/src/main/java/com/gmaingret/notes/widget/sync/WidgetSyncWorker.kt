package com.gmaingret.notes.widget.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gmaingret.notes.domain.repository.BulletRepository
import com.gmaingret.notes.domain.repository.DocumentRepository
import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.NotesWidget
import com.gmaingret.notes.widget.WidgetBullet
import com.gmaingret.notes.widget.WidgetStateStore
import com.gmaingret.notes.widget.stripMarkdownSyntax
import com.gmaingret.notes.widget.WidgetDebugLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Log
import retrofit2.HttpException

private const val TAG = "WidgetSync"

/**
 * Background sync worker that keeps the home screen widget current.
 *
 * Runs as a periodic CoroutineWorker (every 15 minutes) via WorkManager.
 * Uses @HiltWorker so that BulletRepository, DocumentRepository, and WidgetStateStore
 * are injected through Hilt's dependency graph — this works because NotesApplication
 * implements Configuration.Provider and supplies HiltWorkerFactory.
 *
 * Sync logic:
 * 1. Read the pinned document ID from WidgetStateStore.
 * 2. Validate the document still exists in the user's document list.
 * 3. Fetch bullets for the document.
 * 4. Filter to root-level bullets, strip markdown, cap at MAX_BULLETS.
 * 5. Persist bullets + display state to WidgetStateStore.
 * 6. Trigger updateAll() so Glance re-reads the cached state.
 *
 * Error policy:
 * - Auth errors (401): write SESSION_EXPIRED to cache, trigger update.
 * - Document not found: write DOCUMENT_NOT_FOUND to cache, trigger update.
 * - Other network errors: keep existing cache untouched (stale data is better than a blank widget).
 * - All cases: return Result.success() to keep the periodic schedule alive.
 *   (Result.failure() would cause exponential backoff and eventually cancel the schedule.)
 */
@HiltWorker
class WidgetSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val bulletRepository: BulletRepository,
    private val documentRepository: DocumentRepository,
    private val widgetStateStore: WidgetStateStore
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Step 1: Find the configured document ID (returns first entry found)
        val docId = widgetStateStore.getFirstDocumentId()
            ?: return Result.success() // Widget not configured — nothing to do

        // Skip sync if a widget mutation (delete/add) is in flight
        if (widgetStateStore.isMutationInFlight()) {
            WidgetDebugLog.log(applicationContext, TAG, "doWork SKIPPED — mutation in flight")
            return Result.success()
        }

        WidgetDebugLog.log(applicationContext, TAG, "doWork RUNNING for doc=$docId")

        // Step 2: Fetch document list to validate docId and get title
        val documents = try {
            val result = documentRepository.getDocuments()
            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                return if (isAuthError(ex)) {
                    widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
                    triggerWidgetUpdate()
                    Result.success()
                } else {
                    // Non-auth network error — keep stale cache, return success to preserve schedule
                    Result.success()
                }
            }
            result.getOrThrow()
        } catch (e: Exception) {
            return if (isAuthError(e)) {
                widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
                triggerWidgetUpdate()
                Result.success()
            } else {
                Result.success()
            }
        }

        // Step 3: Locate the document in the list
        val document = documents.find { it.id == docId }
        if (document == null) {
            widgetStateStore.saveDisplayState(DisplayState.DOCUMENT_NOT_FOUND)
            triggerWidgetUpdate()
            return Result.success()
        }

        // Step 4: Fetch bullets for the document
        val bullets = try {
            val result = bulletRepository.getBullets(docId)
            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                return if (isAuthError(ex)) {
                    widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
                    triggerWidgetUpdate()
                    Result.success()
                } else {
                    Result.success()
                }
            }
            result.getOrThrow()
        } catch (e: Exception) {
            return if (isAuthError(e)) {
                widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
                triggerWidgetUpdate()
                Result.success()
            } else {
                Result.success()
            }
        }

        // Step 5: Filter root-level bullets, strip markdown, cap at MAX_BULLETS
        val rootBullets = bullets
            .filter { it.parentId == null }
            .take(NotesWidget.MAX_BULLETS)
            .map { bullet ->
                WidgetBullet(
                    id = bullet.id,
                    content = stripMarkdownSyntax(bullet.content),
                    isComplete = bullet.isComplete
                )
            }

        // Step 6: Persist title, bullets, and display state
        WidgetDebugLog.log(applicationContext, TAG, "writing ${rootBullets.size} bullets to store, ids=${rootBullets.map { it.id }}")
        widgetStateStore.saveDocumentTitle(document.title)
        widgetStateStore.saveBullets(rootBullets)
        widgetStateStore.saveDisplayState(
            if (rootBullets.isEmpty()) DisplayState.EMPTY else DisplayState.CONTENT
        )

        // Step 7: Trigger widget re-render from cached state
        triggerWidgetUpdate()

        return Result.success()
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Returns true if the throwable represents an authentication failure (HTTP 401).
     *
     * Mirrors the same logic used in NotesWidget.fetchWidgetData() for consistency.
     */
    private fun isAuthError(e: Throwable?): Boolean {
        if (e == null) return false
        if (e is HttpException && e.code() == 401) return true
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("401") || msg.contains("unauthorized") || msg.contains("unauthenticated")
    }

    /**
     * Calls [NotesWidget.updateAll] to trigger a Glance re-render for all widget instances.
     *
     * After this call, provideGlance() will re-run and read the freshly cached data
     * from WidgetStateStore. Using updateAll() ensures all widget instances are updated,
     * not just a single glanceId.
     */
    private suspend fun triggerWidgetUpdate() {
        try {
            NotesWidget.pushStateToGlance(applicationContext)
        } catch (e: Exception) {
            // Can throw if no widget instances exist — safe to ignore
        }
    }
}
