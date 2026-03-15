package com.gmaingret.notes.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import dagger.hilt.android.EntryPointAccessors
import retrofit2.HttpException

/**
 * Home screen widget for Notes.
 *
 * Uses SizeMode.Responsive with 3 breakpoints so Glance re-renders at each
 * size instead of stretching a single layout.
 *
 * Widget state is stored in Glance preferences (read via currentState inside
 * provideContent) so the composition auto-recomposes when state changes.
 * WidgetStateStore is used as a durable cache for the WorkManager background sync.
 */
class NotesWidget : GlanceAppWidget() {

    companion object {
        /** Maximum number of root bullets to load into the widget. */
        const val MAX_BULLETS = 50

        /** Glance preferences keys. */
        val DOC_ID_KEY = stringPreferencesKey("doc_id")
        val DOC_TITLE_KEY = stringPreferencesKey("doc_title")
        val DISPLAY_STATE_KEY = stringPreferencesKey("display_state")
        val BULLETS_JSON_KEY = stringPreferencesKey("bullets_json")

        // Responsive breakpoints
        private val SMALL = DpSize(200.dp, 100.dp)
        private val MEDIUM = DpSize(276.dp, 220.dp)
        private val LARGE = DpSize(276.dp, 380.dp)

        private val gson = com.google.gson.Gson()
        private val bulletListType = object : com.google.gson.reflect.TypeToken<List<WidgetBullet>>() {}.type

        /**
         * Writes the document ID and fetched data into Glance widget preferences.
         * Glance observes its own preferences — the composition recomposes automatically.
         *
         * Also writes to WidgetStateStore for durable caching (WorkManager reads from there).
         */
        suspend fun setDocumentId(context: Context, appWidgetId: Int, docId: String) {
            val store = WidgetStateStore.getInstance(context)
            store.saveDocumentId(appWidgetId, docId)

            // Fetch data inline so the widget shows content immediately
            var displayState = DisplayState.LOADING
            var bullets: List<WidgetBullet> = emptyList()
            var docTitle = ""
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val widget = NotesWidget()
                when (val result = widget.fetchWidgetData(entryPoint, docId)) {
                    is WidgetUiState.Content -> {
                        displayState = DisplayState.CONTENT
                        bullets = result.bullets
                        docTitle = result.documentTitle
                    }
                    is WidgetUiState.Empty -> {
                        displayState = DisplayState.EMPTY
                    }
                    is WidgetUiState.SessionExpired -> displayState = DisplayState.SESSION_EXPIRED
                    is WidgetUiState.DocumentNotFound -> displayState = DisplayState.DOCUMENT_NOT_FOUND
                    is WidgetUiState.Error -> displayState = DisplayState.ERROR
                    else -> {}
                }
            } catch (_: Exception) {
                // One-shot WorkManager sync will retry later
            }

            // Persist to durable cache (for WorkManager / post-reboot)
            store.saveBullets(bullets)
            store.saveDisplayState(displayState)
            if (docTitle.isNotEmpty()) store.saveDocumentTitle(docTitle)

            // Write to Glance preferences — triggers recomposition of provideContent
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[DOC_ID_KEY] = docId
                prefs[DOC_TITLE_KEY] = docTitle
                prefs[DISPLAY_STATE_KEY] = displayState.name
                prefs[BULLETS_JSON_KEY] = gson.toJson(bullets)
            }
        }

        /**
         * Pushes the current WidgetStateStore cache into Glance preferences for all widgets,
         * then calls update() to trigger recomposition. Called by WidgetSyncWorker and
         * WidgetRefreshHelper after writing to the durable cache.
         */
        suspend fun pushStateToGlance(context: Context) {
            val store = WidgetStateStore.getInstance(context)
            val displayState = store.getDisplayState()
            val bullets = store.getBullets()
            val docId = store.getFirstDocumentId()
            val docTitle = store.getDocumentTitle()
            pushToGlanceDirect(context, displayState, bullets, docId, docTitle)
        }

        /**
         * Fast path: writes pre-computed data directly to Glance preferences
         * without reading from the encrypted DataStore. Used for optimistic
         * UI updates where the caller already has the data in memory.
         */
        suspend fun pushToGlanceDirect(
            context: Context,
            displayState: DisplayState,
            bullets: List<WidgetBullet>,
            docId: String? = null,
            docTitle: String? = null
        ) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(NotesWidget::class.java)
            val widget = NotesWidget()
            for (id in glanceIds) {
                updateAppWidgetState(context, id) { prefs ->
                    prefs[DISPLAY_STATE_KEY] = displayState.name
                    prefs[BULLETS_JSON_KEY] = gson.toJson(bullets)
                    if (docId != null) prefs[DOC_ID_KEY] = docId
                    if (docTitle != null) prefs[DOC_TITLE_KEY] = docTitle
                }
                widget.update(context, id)
            }
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    /**
     * Sets up the Glance composition. Called per widget session.
     *
     * State is read inside provideContent via currentState<Preferences>() so that
     * Glance automatically recomposes when preferences change (via updateAppWidgetState).
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val displayStateName = prefs[DISPLAY_STATE_KEY]
            val bulletsJson = prefs[BULLETS_JSON_KEY]
            val docId = prefs[DOC_ID_KEY]
            val docTitle = prefs[DOC_TITLE_KEY] ?: ""

            val displayState = displayStateName?.let {
                try { DisplayState.valueOf(it) } catch (_: Exception) { null }
            } ?: DisplayState.NOT_CONFIGURED

            val bullets: List<WidgetBullet> = bulletsJson?.let {
                try { gson.fromJson<List<WidgetBullet>>(it, bulletListType) } catch (_: Exception) { null }
            } ?: emptyList()

            val uiState = when (displayState) {
                DisplayState.CONTENT -> WidgetUiState.Content(docId ?: "", docTitle, bullets)
                DisplayState.EMPTY -> WidgetUiState.Empty
                DisplayState.SESSION_EXPIRED -> WidgetUiState.SessionExpired
                DisplayState.DOCUMENT_NOT_FOUND -> WidgetUiState.DocumentNotFound
                DisplayState.ERROR -> WidgetUiState.Error("Sync failed")
                DisplayState.LOADING -> WidgetUiState.Loading
                DisplayState.NOT_CONFIGURED -> WidgetUiState.NotConfigured
            }

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
