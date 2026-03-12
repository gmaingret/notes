package com.gmaingret.notes.domain.repository

import com.gmaingret.notes.domain.model.User

/**
 * Repository interface contract for auth operations.
 * Implemented in data layer (Plan 03).
 */
interface AuthRepository {
    suspend fun register(email: String, password: String): Result<User>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun loginWithGoogle(idToken: String): Result<User>
    suspend fun refresh(): Result<String>  // Returns new access token
    suspend fun logout()
    suspend fun getAccessToken(): String?
    suspend fun isLoggedIn(): Boolean
}
