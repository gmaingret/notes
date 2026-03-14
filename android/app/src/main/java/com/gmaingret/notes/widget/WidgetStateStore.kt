package com.gmaingret.notes.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.gmaingret.notes.data.local.EncryptedDataStoreFactory
import kotlinx.coroutines.flow.firstOrNull

private val Context.widgetStateDataStore by preferencesDataStore(name = "widget_state")

/**
 * Per-widget document ID persistence with Tink encryption.
 *
 * Each home screen widget has its own appWidgetId assigned by the launcher.
 * This store maps appWidgetId → documentId so the widget knows which document
 * to fetch after a process restart or device reboot.
 *
 * Uses the same Tink AES256-GCM pattern as TokenStore for consistency.
 * The keyset is stored in a separate SharedPreferences partition
 * ("widget_state_keyset") to avoid any cross-contamination with auth keys.
 *
 * Thread safety: DataStore internally serializes all writes, so concurrent
 * calls to saveDocumentId are safe. getInstance() uses @Volatile + synchronized
 * double-check for safe singleton initialization.
 */
class WidgetStateStore private constructor(
    private val context: Context,
    private val aead: Aead
) {

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Persists the document ID for a given widget instance.
     *
     * The value is encrypted before writing. Associated data = key name for
     * domain separation (prevents key confusion attacks across widget IDs).
     */
    suspend fun saveDocumentId(appWidgetId: Int, docId: String) {
        val key = prefKey(appWidgetId)
        val adStr = associatedData(appWidgetId)
        val encrypted = EncryptedDataStoreFactory.encrypt(aead, docId, adStr)
        context.widgetStateDataStore.edit { prefs ->
            prefs[key] = encrypted
        }
    }

    /**
     * Returns the persisted document ID for the given widget, or null if not set.
     */
    suspend fun getDocumentId(appWidgetId: Int): String? {
        val key = prefKey(appWidgetId)
        val adStr = associatedData(appWidgetId)
        val encrypted = context.widgetStateDataStore.data.firstOrNull()
            ?.get(key) ?: return null
        return EncryptedDataStoreFactory.decrypt(aead, encrypted, adStr)
    }

    /**
     * Removes the document ID for the given widget.
     *
     * Called by NotesWidgetReceiver.onDeleted() to avoid accumulating orphaned entries.
     */
    suspend fun clearDocumentId(appWidgetId: Int) {
        val key = prefKey(appWidgetId)
        context.widgetStateDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun prefKey(appWidgetId: Int) =
        stringPreferencesKey("widget_doc_$appWidgetId")

    private fun associatedData(appWidgetId: Int) =
        "widget_doc_$appWidgetId"

    // ---------------------------------------------------------------------------
    // Singleton
    // ---------------------------------------------------------------------------

    companion object {
        @Volatile
        private var instance: WidgetStateStore? = null

        /**
         * Returns the process-wide singleton, creating it on first call.
         *
         * Safe to call from any coroutine or thread. Aead initialization is
         * deferred to first use inside the returned instance.
         */
        fun getInstance(context: Context): WidgetStateStore {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val aead = EncryptedDataStoreFactory.getWidgetStateAead(appContext)
                    WidgetStateStore(appContext, aead).also { instance = it }
                }
            }
        }

        /**
         * Test-only factory that accepts an injected Aead (e.g. identity mock).
         * Not accessible from production code — use getInstance() there.
         */
        internal fun createForTest(context: Context, aead: Aead): WidgetStateStore {
            return WidgetStateStore(context, aead)
        }
    }
}
