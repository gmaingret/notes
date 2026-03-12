package com.gmaingret.notes.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")

        private const val AD_ACCESS_TOKEN = "access_token"
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
