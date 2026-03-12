package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.AuthApi
import com.gmaingret.notes.data.api.GoogleTokenRequest
import com.gmaingret.notes.data.api.LoginRequest
import com.gmaingret.notes.data.api.RegisterRequest
import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.domain.model.User
import com.gmaingret.notes.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of AuthRepository.
 *
 * Persists the access token and user email to encrypted DataStore after every
 * successful auth operation so that:
 * - TokenStore.getAccessToken() always returns a valid token for AuthInterceptor
 * - TokenStore.getUserEmail() provides the greeting on MainScreen without an
 *   extra network call
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore
) : AuthRepository {

    override suspend fun register(email: String, password: String): Result<User> {
        return try {
            val response = authApi.register(RegisterRequest(email, password))
            tokenStore.saveAccessToken(response.accessToken)
            tokenStore.saveUserEmail(response.user.email)
            Result.success(User(id = response.user.id, email = response.user.email))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = authApi.login(LoginRequest(email, password))
            tokenStore.saveAccessToken(response.accessToken)
            tokenStore.saveUserEmail(response.user.email)
            Result.success(User(id = response.user.id, email = response.user.email))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginWithGoogle(idToken: String): Result<User> {
        return try {
            val response = authApi.googleToken(GoogleTokenRequest(idToken))
            tokenStore.saveAccessToken(response.accessToken)
            tokenStore.saveUserEmail(response.user.email)
            Result.success(User(id = response.user.id, email = response.user.email))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refresh(): Result<String> {
        return try {
            val response = authApi.refresh()
            tokenStore.saveAccessToken(response.accessToken)
            Result.success(response.accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        try {
            authApi.logout()
        } catch (_: Exception) {
            // Ignore network errors on logout — always clear local state
        }
        tokenStore.clearAll()
    }

    override suspend fun getAccessToken(): String? = tokenStore.getAccessToken()

    override suspend fun isLoggedIn(): Boolean = tokenStore.getAccessToken() != null
}
