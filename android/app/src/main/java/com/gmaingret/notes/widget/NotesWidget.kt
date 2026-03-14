package com.gmaingret.notes.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Home screen widget for Notes.
 *
 * Uses SizeMode.Responsive with 3 breakpoints so Glance re-renders at each
 * size instead of stretching a single layout.
 *
 * Document ID is stored in both Glance widget preferences (for the update() trigger)
 * and in WidgetStateStore. provideGlance reads exclusively from WidgetStateStore cache
 * — no live API calls happen inside provideGlance. All data fetching is delegated to
 * WidgetSyncWorker (periodic) or triggerWidgetRefreshIfNeeded (in-app mutation trigger).
 */
class NotesWidget : GlanceAppWidget() {

    companion object {
        /** Maximum number of root bullets to load into the widget. */
        const val MAX_BULLETS = 50

        /** Glance preferences key for the selected document ID. */
        val DOC_ID_KEY = stringPreferencesKey("doc_id")

        // Responsive breakpoints
        private val SMALL = DpSize(200.dp, 100.dp)
        private val MEDIUM = DpSize(276.dp, 220.dp)
        private val LARGE = DpSize(276.dp, 380.dp)

        /**
         * Writes the document ID into Glance widget preferences and triggers
         * a widget update. Call from the config activity after saving to WidgetStateStore.
         *
         * Note: provideGlance no longer reads DOC_ID_KEY from Glance preferences —
         * it reads from WidgetStateStore instead. The update() call here triggers
         * provideGlance to re-run and refresh from cache.
         */
        suspend fun setDocumentId(context: Context, appWidgetId: Int, docId: String) {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[DOC_ID_KEY] = docId
            }
            NotesWidget().update(context, glanceId)
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    /**
     * Reads the cached display state and bullets from WidgetStateStore and renders them.
     *
     * No live API calls happen here. All I/O runs with withContext(Dispatchers.IO) before
     * provideContent{} per the architecture rule that I/O must not happen inside the
     * provideContent lambda.
     *
     * The cache is populated by:
     * - WidgetSyncWorker (periodic 15-min background sync)
     * - triggerWidgetRefreshIfNeeded() in BulletTreeViewModel (after every bullet mutation)
     * - Immediate one-shot sync enqueued by WidgetConfigActivity after document selection
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetStateStore.getInstance(context)
        val displayState = withContext(Dispatchers.IO) { store.getDisplayState() }
        val cachedBullets = withContext(Dispatchers.IO) { store.getBullets() }
        val docId = withContext(Dispatchers.IO) { store.getFirstDocumentId() }

        val uiState = when (displayState) {
            DisplayState.CONTENT -> WidgetUiState.Content(docId ?: "", "", cachedBullets)
            DisplayState.EMPTY -> WidgetUiState.Empty
            DisplayState.SESSION_EXPIRED -> WidgetUiState.SessionExpired
            DisplayState.DOCUMENT_NOT_FOUND -> WidgetUiState.DocumentNotFound
            DisplayState.ERROR -> WidgetUiState.Error("Sync failed")
            DisplayState.LOADING -> WidgetUiState.Loading
            DisplayState.NOT_CONFIGURED -> WidgetUiState.NotConfigured
        }

        provideContent {
            GlanceTheme(colors = NotesWidgetColorScheme.colors) {
                WidgetContent(uiState = uiState, context = context)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Data fetching — extracted for unit testability
    // ---------------------------------------------------------------------------

    /**
     * Fetches widget data and maps it to a [WidgetUiState].
     *
     * @param entryPoint Hilt entry point providing repository access.
     * @param docId The persisted document ID, or null if widget is not yet configured.
     */
    suspend fun fetchWidgetData(
        entryPoint: WidgetEntryPoint,
        docId: String?
    ): WidgetUiState {
        if (docId == null) return WidgetUiState.NotConfigured

        // Fetch document list to validate the docId still exists and get title
        val documents = try {
            val result = entryPoint.documentRepository().getDocuments()
            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                return if (isAuthError(ex)) {
                    WidgetUiState.SessionExpired
                } else {
                    WidgetUiState.Error(ex?.message ?: "Unknown error")
                }
            }
            result.getOrThrow()
        } catch (e: Exception) {
            return if (isAuthError(e)) {
                WidgetUiState.SessionExpired
            } else {
                WidgetUiState.Error(e.message ?: "Unknown error")
            }
        }

        val document = documents.find { it.id == docId }
            ?: return WidgetUiState.DocumentNotFound

        // Fetch bullets for the document
        val bullets = try {
            val result = entryPoint.bulletRepository().getBullets(docId)
            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                return if (isAuthError(ex)) {
                    WidgetUiState.SessionExpired
                } else {
                    WidgetUiState.Error(ex?.message ?: "Unknown error")
                }
            }
            result.getOrThrow()
        } catch (e: Exception) {
            return if (isAuthError(e)) {
                WidgetUiState.SessionExpired
            } else {
                WidgetUiState.Error(e.message ?: "Unknown error")
            }
        }

        // Filter to root-level bullets only (parentId == null), cap at MAX_BULLETS
        val rootBullets = bullets
            .filter { it.parentId == null }
            .take(MAX_BULLETS)
            .map { bullet ->
                WidgetBullet(
                    id = bullet.id,
                    content = stripMarkdownSyntax(bullet.content),
                    isComplete = bullet.isComplete
                )
            }

        return if (rootBullets.isEmpty()) {
            WidgetUiState.Empty
        } else {
            WidgetUiState.Content(
                documentId = docId,
                documentTitle = document.title,
                bullets = rootBullets
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun isAuthError(e: Throwable?): Boolean {
        if (e == null) return false
        if (e is HttpException && e.code() == 401) return true
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("401") || msg.contains("unauthorized") || msg.contains("unauthenticated")
    }
}
