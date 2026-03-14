package com.gmaingret.notes.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gmaingret.notes.data.local.EncryptedDataStoreFactory
import kotlinx.coroutines.flow.firstOrNull

private val Context.widgetStateDataStore by preferencesDataStore(name = "widget_state")

/**
 * Represents the logical display state of the widget, persisted between process restarts.
 *
 * Written by WidgetSyncWorker after each background sync. Read by NotesWidget.provideGlance()
 * to render from cache without an additional network call.
 */
enum class DisplayState {
    NOT_CONFIGURED,
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
    SESSION_EXPIRED,
    DOCUMENT_NOT_FOUND
}

/**
 * Per-widget document ID, bullet cache, and display state persistence with Tink encryption.
 *
 * Each home screen widget has its own appWidgetId assigned by the launcher.
 * This store maps appWidgetId → documentId so the widget knows which document
 * to fetch after a process restart or device reboot.
 *
 * In addition to per-widget document IDs, the store also holds shared widget cache:
 * - bullets_json: latest fetched bullets (Gson-serialized List<WidgetBullet>, encrypted)
 * - display_state: latest display state name (encrypted)
 *
 * Uses the same Tink AES256-GCM pattern as TokenStore for consistency.
 * The keyset is stored in a separate SharedPreferences partition
 * ("widget_state_keyset") to avoid any cross-contamination with auth keys.
 *
 * Thread safety: DataStore internally serializes all writes, so concurrent
 * calls to save* methods are safe. getInstance() uses @Volatile + synchronized
 * double-check for safe singleton initialization.
 */
class WidgetStateStore private constructor(
    private val context: Context,
    private val aead: Aead
) {

    companion object {
        private val DISPLAY_STATE_KEY = stringPreferencesKey("display_state")
        private val BULLETS_KEY = stringPreferencesKey("bullets_json")

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

    // ---------------------------------------------------------------------------
    // Document ID (per-widget)
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

    /**
     * Returns the document ID for any widget that has been configured, or null if none.
     *
     * Used by WidgetSyncWorker which has no appWidgetId in its context.
     * Reads all DataStore entries and returns the first decrypted value from a
     * "widget_doc_*" key.
     */
    suspend fun getFirstDocumentId(): String? {
        val prefs = context.widgetStateDataStore.data.firstOrNull() ?: return null
        for ((key, value) in prefs.asMap()) {
            val keyName = key.name
            if (keyName.startsWith("widget_doc_")) {
                val widgetId = keyName.removePrefix("widget_doc_").toIntOrNull() ?: continue
                val adStr = associatedData(widgetId)
                val decrypted = EncryptedDataStoreFactory.decrypt(aead, value as String, adStr)
                if (decrypted != null) return decrypted
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------
    // Bullet cache (shared across all widget instances)
    // ---------------------------------------------------------------------------

    /**
     * Persists the cached list of bullets serialized as JSON with Gson, encrypted with Tink.
     *
     * Written by WidgetSyncWorker after each background sync. Read by NotesWidget
     * to render immediately from cache without a network call.
     */
    suspend fun saveBullets(bullets: List<WidgetBullet>) {
        val json = Gson().toJson(bullets)
        val encrypted = EncryptedDataStoreFactory.encrypt(aead, json, "bullets_json")
        context.widgetStateDataStore.edit { prefs ->
            prefs[BULLETS_KEY] = encrypted
        }
    }

    /**
     * Returns the cached bullet list, or an empty list if nothing is stored.
     */
    suspend fun getBullets(): List<WidgetBullet> {
        val encrypted = context.widgetStateDataStore.data.firstOrNull()
            ?.get(BULLETS_KEY) ?: return emptyList()
        val json = EncryptedDataStoreFactory.decrypt(aead, encrypted, "bullets_json")
            ?: return emptyList()
        val type = object : TypeToken<List<WidgetBullet>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }

    // ---------------------------------------------------------------------------
    // Display state (shared across all widget instances)
    // ---------------------------------------------------------------------------

    /**
     * Persists the current display state. Written by WidgetSyncWorker after each sync.
     */
    suspend fun saveDisplayState(state: DisplayState) {
        val encrypted = EncryptedDataStoreFactory.encrypt(aead, state.name, "display_state")
        context.widgetStateDataStore.edit { prefs ->
            prefs[DISPLAY_STATE_KEY] = encrypted
        }
    }

    /**
     * Returns the persisted display state, or [DisplayState.NOT_CONFIGURED] if nothing is stored.
     */
    suspend fun getDisplayState(): DisplayState {
        val encrypted = context.widgetStateDataStore.data.firstOrNull()
            ?.get(DISPLAY_STATE_KEY) ?: return DisplayState.NOT_CONFIGURED
        val name = EncryptedDataStoreFactory.decrypt(aead, encrypted, "display_state")
            ?: return DisplayState.NOT_CONFIGURED
        return try {
            DisplayState.valueOf(name)
        } catch (e: IllegalArgumentException) {
            DisplayState.NOT_CONFIGURED
        }
    }

    // ---------------------------------------------------------------------------
    // Clear all
    // ---------------------------------------------------------------------------

    /**
     * Clears all data from the DataStore — document IDs, bullet cache, and display state.
     *
     * Intended for use when all widgets are removed (NotesWidgetReceiver.onDeleted).
     */
    suspend fun clearAll() {
        context.widgetStateDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun prefKey(appWidgetId: Int) =
        stringPreferencesKey("widget_doc_$appWidgetId")

    private fun associatedData(appWidgetId: Int) =
        "widget_doc_$appWidgetId"
}
