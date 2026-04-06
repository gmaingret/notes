package com.gmaingret.notes.data.api

import android.util.Base64
import com.gmaingret.notes.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that adds the Authorization: Bearer header to every request.
 *
 * For non-auth endpoints, proactively refreshes the access token if it expires
 * within [EXPIRY_BUFFER_SECONDS] — avoids a wasted 401 round trip.
 * Auth endpoints skip proactive refresh to avoid recursion through the interceptor chain.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApi: dagger.Lazy<AuthApi>
) : Interceptor {

    private val mutex = Mutex()

    companion object {
        private const val EXPIRY_BUFFER_SECONDS = 120L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        val isAuthEndpoint = path.startsWith("/api/auth/")

        val token = runBlocking {
            if (isAuthEndpoint) {
                tokenStore.getAccessToken()
            } else {
                ensureValidToken()
            }
        }

        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }

    /**
     * Returns a valid access token, refreshing proactively if it expires soon.
     * Uses a mutex so only one refresh happens if multiple requests fire concurrently.
     */
    private suspend fun ensureValidToken(): String? {
        val token = tokenStore.getAccessToken() ?: return null

        // Fast path: token still valid with margin — no lock needed
        val exp = parseExp(token)
        if (exp != null && exp - System.currentTimeMillis() / 1000 > EXPIRY_BUFFER_SECONDS) {
            return token
        }

        // Token expired or expiring soon — acquire lock and refresh
        return mutex.withLock {
            // Re-check after acquiring lock — another coroutine may have refreshed
            val currentToken = tokenStore.getAccessToken() ?: return@withLock null
            val currentExp = parseExp(currentToken)
            if (currentExp != null && currentExp - System.currentTimeMillis() / 1000 > EXPIRY_BUFFER_SECONDS) {
                return@withLock currentToken
            }

            // Refresh
            val refreshToken = tokenStore.getRefreshToken() ?: return@withLock null
            try {
                val response = authApi.get().refresh(RefreshTokenRequest(refreshToken))
                tokenStore.saveAccessToken(response.accessToken)
                response.accessToken
            } catch (e: Exception) {
                // Proactive refresh failed — return the current token anyway.
                // If it's truly expired, TokenAuthenticator will handle the 401.
                currentToken
            }
        }
    }

    private fun parseExp(jwt: String): Long? {
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
