package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.AuthResponse
import com.gmaingret.notes.data.model.RefreshResponse
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

// Request body data classes
data class RegisterRequest(
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class GoogleTokenRequest(
    @SerializedName("idToken")
    val idToken: String
)

/**
 * Retrofit service interface for backend auth API.
 * All endpoints match the existing server/src/routes/auth.ts contracts.
 *
 * Base URL: https://notes.gregorymaingret.fr
 * Refresh token is stored as httpOnly cookie named "refreshToken" — managed by CookieJar.
 */
interface AuthApi {

    /**
     * POST /api/auth/register
     * Body: {email, password}
     * Response: 201 {accessToken, user: {id, email}} | 400 {errors} | 409 {field, message}
     */
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    /**
     * POST /api/auth/login
     * Body: {email, password}
     * Response: 200 {accessToken, user: {id, email}} | 401 {error}
     */
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    /**
     * POST /api/auth/google/token
     * Body: {idToken}
     * Response: 200 {accessToken, user: {id, email}} | 401 {error}
     * Note: New endpoint added in Phase 9 backend work (Plan 02).
     */
    @POST("api/auth/google/token")
    suspend fun googleToken(@Body request: GoogleTokenRequest): AuthResponse

    /**
     * POST /api/auth/refresh
     * Uses httpOnly refreshToken cookie (sent automatically via CookieJar)
     * Response: 200 {accessToken} | 401 {error}
     */
    @POST("api/auth/refresh")
    suspend fun refresh(): RefreshResponse

    /**
     * POST /api/auth/logout
     * Response: 200 {message}
     */
    @POST("api/auth/logout")
    suspend fun logout()
}
