package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.local.TokenStore
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator that handles 401 responses by refreshing the access token.
 *
 * Uses a Mutex to prevent concurrent refresh races: if multiple requests fail with
 * 401 simultaneously, only one refresh call is made. Others wait, then detect the
 * token has already been refreshed (stale token check) and retry with the new token.
 *
 * Uses dagger.Lazy<AuthApi> to break the circular DI dependency:
 *   OkHttpClient -> TokenAuthenticator -> AuthApi -> OkHttpClient
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApi: Lazy<AuthApi>
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Never retry auth on auth endpoints — the refresh call itself returns 401
        // when the session is dead. Retrying would deadlock on the Mutex.
        val path = response.request.url.encodedPath
        if (path.startsWith("/api/auth/")) return null

        // If the request didn't have an Authorization header, this is not our request
        val requestToken = response.request.header("Authorization") ?: return null

        return runBlocking {
            mutex.withLock {
                // Check if token has already been refreshed by another concurrent request
                val currentToken = tokenStore.getAccessToken()
                if (currentToken == null) {
                    // Tokens were cleared — force logout, propagate 401
                    return@runBlocking null
                }

                val requestBearerToken = requestToken.removePrefix("Bearer ")
                if (currentToken != requestBearerToken) {
                    // Another coroutine already refreshed — retry with the new token
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                // Same token in store as in failed request — we need to refresh
                try {
                    val refreshToken = tokenStore.getRefreshToken()
                    if (refreshToken == null) {
                        tokenStore.clearAll()
                        return@runBlocking null
                    }

                    val refreshResponse = authApi.get().refresh(RefreshTokenRequest(refreshToken))
                    tokenStore.saveAccessToken(refreshResponse.accessToken)

                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshResponse.accessToken}")
                        .build()
                } catch (e: Exception) {
                    // Refresh failed — clear all tokens and propagate 401
                    tokenStore.clearAll()
                    null
                }
            }
        }
    }
}
