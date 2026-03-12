package com.gmaingret.notes.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CookieJar"
private val Context.cookieDataStore by preferencesDataStore(name = "cookie_jar")

/**
 * Persistent OkHttp CookieJar backed by encrypted DataStore.
 *
 * Solves the critical blocker: JavaNetCookieJar only holds cookies in memory
 * and loses the refreshToken cookie on process death. This implementation
 * encrypts each cookie as a JSON string and persists it to DataStore.
 *
 * Handles the production server's Set-Cookie format:
 *   refreshToken=...; Path=/; HttpOnly; Secure; SameSite=Strict (maxAge=7 days)
 *
 * runBlocking is used intentionally — OkHttp calls CookieJar on its own
 * thread pool, never on the main thread.
 */
@Singleton
class DataStoreCookieJar @Inject constructor(
    @ApplicationContext private val context: Context
) : CookieJar {

    private val gson = Gson()
    private val aead: Aead by lazy { EncryptedDataStoreFactory.getCookieJarAead(context) }

    // ---------------------------------------------------------------------------
    // StoredCookie — serialization model
    // ---------------------------------------------------------------------------

    data class StoredCookie(
        @SerializedName("name") val name: String,
        @SerializedName("value") val value: String,
        @SerializedName("expiresAt") val expiresAt: Long,
        @SerializedName("domain") val domain: String,
        @SerializedName("path") val path: String,
        @SerializedName("secure") val secure: Boolean,
        @SerializedName("httpOnly") val httpOnly: Boolean
    )

    // ---------------------------------------------------------------------------
    // CookieJar interface
    // ---------------------------------------------------------------------------

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Only persist session/persistent cookies (not purely session cookies
        // with expiresAt == Long.MIN_VALUE indicate no Max-Age/Expires header).
        // We persist everything that has an expiry OR httpOnly cookies that carry
        // the refresh token (the server always sets maxAge=7 days).
        Log.d(TAG, "saveFromResponse url=${url.host} cookies=${cookies.size}")
        for (c in cookies) {
            Log.d(TAG, "  cookie: name=${c.name} persistent=${c.persistent} expiresAt=${c.expiresAt} domain=${c.domain} httpOnly=${c.httpOnly}")
        }
        val persistent = cookies.filter { it.persistent }
        if (persistent.isEmpty()) {
            Log.w(TAG, "  NO persistent cookies to save!")
            return
        }

        runBlocking {
            context.cookieDataStore.edit { prefs ->
                for (cookie in persistent) {
                    val stored = StoredCookie(
                        name = cookie.name,
                        value = cookie.value,
                        expiresAt = cookie.expiresAt,
                        domain = cookie.domain,
                        path = cookie.path,
                        secure = cookie.secure,
                        httpOnly = cookie.httpOnly
                    )
                    val json = gson.toJson(stored)
                    val key = "${cookie.domain}|${cookie.name}"
                    val encrypted = EncryptedDataStoreFactory.encrypt(aead, json, key)
                    prefs[stringPreferencesKey(key)] = encrypted
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        Log.d(TAG, "loadForRequest url=${url.host}${url.encodedPath}")
        val now = System.currentTimeMillis()
        val prefs = runBlocking {
            context.cookieDataStore.data.firstOrNull()
        }
        if (prefs == null) {
            Log.w(TAG, "  prefs is null!")
            return emptyList()
        }
        Log.d(TAG, "  prefs keys: ${prefs.asMap().keys.map { it.name }}")

        val result = mutableListOf<Cookie>()

        for ((prefKey, encryptedValue) in prefs.asMap()) {
            val key = prefKey.name
            // Only load cookies for matching host
            if (!key.startsWith("${url.host}|")) continue

            val json = EncryptedDataStoreFactory.decrypt(aead, encryptedValue as String, key)
                ?: continue

            val stored = try {
                gson.fromJson(json, StoredCookie::class.java)
            } catch (e: Exception) {
                continue
            }

            // Filter expired cookies
            if (stored.expiresAt <= now) continue

            val cookie = Cookie.Builder()
                .name(stored.name)
                .value(stored.value)
                .expiresAt(stored.expiresAt)
                .domain(stored.domain)
                .path(stored.path)
                .apply {
                    if (stored.secure) secure()
                    if (stored.httpOnly) httpOnly()
                }
                .build()

            result.add(cookie)
        }

        Log.d(TAG, "  returning ${result.size} cookies: ${result.map { it.name }}")
        return result
    }

    /**
     * Removes all stored cookies (called on logout via TokenStore.clearAll in
     * AuthRepositoryImpl — callers may optionally call this directly).
     */
    suspend fun clearAll() {
        context.cookieDataStore.edit { it.clear() }
    }
}
