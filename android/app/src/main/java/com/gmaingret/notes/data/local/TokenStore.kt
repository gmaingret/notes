package com.gmaingret.notes.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authTokenDataStore by preferencesDataStore(name = "auth_tokens")

/**
 * Persistent, encrypted storage for the access token and user email.
 *
 * Values are encrypted with Tink AES256-GCM before being written to DataStore,
 * so they survive process death without being readable in plain text on disk.
 *
 * Used by:
 * - AuthInterceptor: reads access token before every request
 * - AuthRepositoryImpl: writes token + email after successful auth
 * - TokenAuthenticator: reads/clears token on 401
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val aead: Aead by lazy { EncryptedDataStoreFactory.getAuthTokensAead(context) }

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_LAST_DOC_ID = stringPreferencesKey("last_doc_id")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        private const val AD_ACCESS_TOKEN = "access_token"
        private const val AD_REFRESH_TOKEN = "refresh_token"
        private const val AD_USER_EMAIL = "user_email"
    }

    // ---------------------------------------------------------------------------
    // Access Token
    // ---------------------------------------------------------------------------

    suspend fun getAccessToken(): String? {
        val encrypted = context.authTokenDataStore.data.firstOrNull()
            ?.get(KEY_ACCESS_TOKEN) ?: return null
        return EncryptedDataStoreFactory.decrypt(aead, encrypted, AD_ACCESS_TOKEN)
    }

    suspend fun saveAccessToken(token: String) {
        val encrypted = EncryptedDataStoreFactory.encrypt(aead, token, AD_ACCESS_TOKEN)
        context.authTokenDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = encrypted
        }
    }

    // ---------------------------------------------------------------------------
    // Refresh Token
    // ---------------------------------------------------------------------------

    suspend fun getRefreshToken(): String? {
        val encrypted = context.authTokenDataStore.data.firstOrNull()
            ?.get(KEY_REFRESH_TOKEN) ?: return null
        return EncryptedDataStoreFactory.decrypt(aead, encrypted, AD_REFRESH_TOKEN)
    }

    suspend fun saveRefreshToken(token: String) {
        val encrypted = EncryptedDataStoreFactory.encrypt(aead, token, AD_REFRESH_TOKEN)
        context.authTokenDataStore.edit { prefs ->
            prefs[KEY_REFRESH_TOKEN] = encrypted
        }
    }

    // ---------------------------------------------------------------------------
    // User Email
    // ---------------------------------------------------------------------------

    /**
     * Returns the stored email for the logged-in user (used by MainScreen greeting
     * without needing an extra network call).
     */
    suspend fun getUserEmail(): String? {
        val encrypted = context.authTokenDataStore.data.firstOrNull()
            ?.get(KEY_USER_EMAIL) ?: return null
        return EncryptedDataStoreFactory.decrypt(aead, encrypted, AD_USER_EMAIL)
    }

    /**
     * Persists the user's email after successful login, register, or Google auth.
     */
    suspend fun saveUserEmail(email: String) {
        val encrypted = EncryptedDataStoreFactory.encrypt(aead, email, AD_USER_EMAIL)
        context.authTokenDataStore.edit { prefs ->
            prefs[KEY_USER_EMAIL] = encrypted
        }
    }

    // ---------------------------------------------------------------------------
    // Last Opened Document ID
    // ---------------------------------------------------------------------------

    /**
     * Persists the ID of the last document opened by the user.
     *
     * Plain (unencrypted) DataStore write — doc ID is a non-sensitive UUID.
     * Cleared automatically by [clearAll] on logout.
     */
    suspend fun saveLastDocId(docId: String) {
        context.authTokenDataStore.edit { prefs -> prefs[KEY_LAST_DOC_ID] = docId }
    }

    /**
     * Returns the ID of the last document opened, or null if none was recorded.
     *
     * Plain (unencrypted) DataStore read — doc ID is a non-sensitive UUID.
     */
    suspend fun getLastDocId(): String? =
        context.authTokenDataStore.data.firstOrNull()?.get(KEY_LAST_DOC_ID)

    // ---------------------------------------------------------------------------
    // Theme Mode
    // ---------------------------------------------------------------------------

    suspend fun saveThemeMode(mode: String) {
        context.authTokenDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode }
    }

    suspend fun getThemeMode(): String =
        context.authTokenDataStore.data.firstOrNull()?.get(KEY_THEME_MODE) ?: "system"

    fun themeModeFlow() = context.authTokenDataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "system"
    }

    // ---------------------------------------------------------------------------
    // Clear
    // ---------------------------------------------------------------------------

    /**
     * Clears the access token, user email, and any other cached auth data.
     * Called on logout and on failed token refresh.
     */
    suspend fun clearAll() {
        context.authTokenDataStore.edit { it.clear() }
    }
}
