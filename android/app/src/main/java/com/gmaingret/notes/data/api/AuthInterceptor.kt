package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenRefresher: TokenRefresher,
    private val tokenStore: TokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        val isAuthEndpoint = path.startsWith("/api/auth/")

        val token = runBlocking {
            if (isAuthEndpoint) tokenStore.getAccessToken()
            else tokenRefresher.ensureValidToken()
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
}
