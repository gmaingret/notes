package com.gmaingret.notes.data.api

import android.util.Base64
import android.util.Log
import com.gmaingret.notes.data.local.TokenStore
import dagger.Lazy
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralizes token refresh logic with proactive expiry checking.
 *
 * - [ensureValidToken]: called by AuthInterceptor before every request.
 *   If the token expires within 60 seconds, refreshes proactively with automatic
 *   retry (up to [REFRESH_MAX_ATTEMPTS] total) to handle device wake-up scenarios
 *   where the network is briefly unavailable.
 * - [forceRefresh]: called by TokenAuthenticator on 401 (fallback for
 *   clock skew or edge cases). Uses stale-token dedup to avoid redundant refreshes.
 *
 * Uses Lazy<AuthApi> to break the circular DI dependency:
 *   OkHttpClient -> AuthInterceptor -> TokenRefresher -> AuthApi -> OkHttpClient
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApi: Lazy<AuthApi>
) {
    private val mutex = Mutex()

    companion object {
        private const val TAG = "TokenRefresher"
        /** Refresh proactively if token expires within this many seconds. */
        private const val EXPIRY_BUFFER_SECONDS = 60L
        /** Total refresh attempts before giving up (1 initial + 2 retries). */
        private const val REFRESH_MAX_ATTEMPTS = 3
        /** Delays in ms between attempts: 500ms then 1000ms. */
        private val REFRESH_RETRY_DELAYS_MS = longArrayOf(500L, 1000L)
    }

    /**
     * Returns a valid access token, refreshing proactively if it expires soon.
     * Retries the refresh up to [REFRESH_MAX_ATTEMPTS] times with short delays to
     * handle the common "device just woke up, network not ready yet" scenario.
     * Called by AuthInterceptor before every request.
     */
    suspend fun ensureValidToken(): String? {
        val token = tokenStore.getAccessToken() ?: return null

        // Fast path: token is still valid with margin — no lock needed
        val exp = parseExp(token)
        if (exp != null && exp - System.currentTimeMillis() / 1000 > EXPIRY_BUFFER_SECONDS) {
            return token
        }

        // Token expired or expiring soon — acquire lock and refresh with retry
        return mutex.withLock {
            // Re-check after acquiring lock — another coroutine may have refreshed
            val currentToken = tokenStore.getAccessToken() ?: return@withLock null
            val currentExp = parseExp(currentToken)
            if (currentExp != null && currentExp - System.currentTimeMillis() / 1000 > EXPIRY_BUFFER_SECONDS) {
                return@withLock currentToken
            }

            for (attempt in 0 until REFRESH_MAX_ATTEMPTS) {
                if (attempt > 0) {
                    val delayMs = REFRESH_RETRY_DELAYS_MS[attempt - 1]
                    Log.d(TAG, "Retrying refresh in ${delayMs}ms (attempt ${attempt + 1}/$REFRESH_MAX_ATTEMPTS)")
                    delay(delayMs)
                }
                val result = doRefresh()
                if (result != null) return@withLock result        // success
                if (tokenStore.getAccessToken() == null) return@withLock null  // auth failure, session cleared
                // Transient failure — try again
                Log.d(TAG, "Refresh attempt ${attempt + 1} failed transiently, will retry")
            }
            // All attempts exhausted — return null so caller sees network error on next request
            Log.w(TAG, "All $REFRESH_MAX_ATTEMPTS refresh attempts failed")
            null
        }
    }

    /**
     * Forces a token refresh, deduplicating by stale token.
     * Called by TokenAuthenticator on 401.
     *
     * @param failedToken the token that caused the 401
     * @return the new token, or null if refresh failed (tokens cleared)
     */
    suspend fun forceRefresh(failedToken: String): String? {
        return mutex.withLock {
            // Stale-token dedup: if the stored token differs from the failed one,
            // another coroutine already refreshed — return the new token
            val currentToken = tokenStore.getAccessToken()
            if (currentToken != null && currentToken != failedToken) {
                return@withLock currentToken
            }

            doRefresh()
        }
    }

    /**
     * Calls the refresh endpoint and saves the new token.
     * Must be called while holding [mutex].
     *
     * Returns:
     * - the new access token on success
     * - null on definitive auth failure (401/403): tokens are cleared, user must log in
     * - null on transient failure (network error): tokens are kept for the next attempt
     */
    private suspend fun doRefresh(): String? {
        return try {
            val response = authApi.get().refresh()
            val newToken = response.accessToken
            tokenStore.saveAccessToken(newToken)
            newToken
        } catch (e: HttpException) {
            // Definitive auth failure (401/403) — session is truly dead, clear tokens
            Log.w(TAG, "Token refresh failed with HTTP ${e.code()}, clearing session")
            tokenStore.clearAll()
            null
        } catch (e: Exception) {
            // Transient error (network timeout, DNS, etc.) — keep tokens so the next
            // attempt can retry; caller decides whether to retry or propagate
            Log.w(TAG, "Token refresh failed (transient): ${e.javaClass.simpleName}", e)
            null
        }
    }

    /**
     * Parses the `exp` claim from a JWT without verifying the signature.
     * Returns epoch seconds, or null if parsing fails.
     */
    internal fun parseExp(jwt: String): Long? {
        return try {
            val parts = jwt.split(".")
            if (parts.size != 3) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP))
            JSONObject(payload).getLong("exp")
        } catch (e: Exception) {
            null
        }
    }
}
