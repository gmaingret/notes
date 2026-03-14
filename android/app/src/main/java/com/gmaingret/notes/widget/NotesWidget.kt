package com.gmaingret.notes.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Home screen widget for Notes.
 *
 * Uses SizeMode.Responsive with 3 breakpoints so Glance re-renders at each
 * size instead of stretching a single layout. I/O is done inside provideGlance()
 * via withContext(Dispatchers.IO) — never inside the provideContent{} lambda.
 *
 * Data fetching is extracted into a testable suspend function [fetchWidgetData]
 * that accepts a WidgetEntryPoint interface, making it mockable in unit tests
 * without needing a real Android context.
 */
class NotesWidget : GlanceAppWidget() {

    companion object {
        /** Maximum number of root bullets to load into the widget. */
        const val MAX_BULLETS = 50

        // Responsive breakpoints
        private val SMALL = DpSize(200.dp, 100.dp)
        private val MEDIUM = DpSize(276.dp, 220.dp)
        private val LARGE = DpSize(276.dp, 380.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )

        val appWidgetId = id.hashCode() // used to look up saved document selection
        val docId = withContext(Dispatchers.IO) {
            WidgetStateStore.getInstance(context).getDocumentId(appWidgetId)
        }

        val uiState = withContext(Dispatchers.IO) {
            fetchWidgetData(entryPoint, docId)
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
        // Also check message for auth-related strings (e.g. from custom exceptions)
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("401") || msg.contains("unauthorized") || msg.contains("unauthenticated")
    }
}
