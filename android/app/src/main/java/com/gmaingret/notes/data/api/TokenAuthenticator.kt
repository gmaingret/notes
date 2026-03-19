package com.gmaingret.notes.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator that handles 401 responses by delegating to TokenRefresher.
 *
 * This is a fallback for cases where the proactive refresh in AuthInterceptor
 * didn't prevent a 401 (clock skew, server-side revocation, etc.).
 * TokenRefresher owns the Mutex and stale-token dedup logic.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenRefresher: TokenRefresher
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val authHeader = response.request.header("Authorization") ?: return null
        val failedToken = authHeader.removePrefix("Bearer ")

        val newToken = runBlocking { tokenRefresher.forceRefresh(failedToken) }
            ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }
}
