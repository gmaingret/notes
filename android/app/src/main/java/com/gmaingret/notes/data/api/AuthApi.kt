package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.AuthResponse
import com.gmaingret.notes.data.model.RefreshResponse
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

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

data class RefreshTokenRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

data class LogoutTokenRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

/**
 * Retrofit service interface for backend auth API.
 * Base URL: https://notes.gregorymaingret.fr/
 *
 * Auth endpoints return refreshToken in the JSON body (register/login/google).
 * The client stores it in encrypted DataStore and sends it explicitly
 * on refresh/logout — no cookies involved.
 */
interface AuthApi {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/google/token")
    suspend fun googleToken(@Body request: GoogleTokenRequest): AuthResponse

    /**
     * POST /api/auth/refresh/token
     * Sends refresh token in body, returns new access token.
     * Refresh token is NOT rotated — it stays valid for its full 7-day lifetime.
     */
    @POST("api/auth/refresh/token")
    suspend fun refresh(@Body request: RefreshTokenRequest): RefreshResponse

    /**
     * POST /api/auth/logout/token
     * Sends refresh token in body so server can revoke it.
     */
    @POST("api/auth/logout/token")
    suspend fun logout(@Body request: LogoutTokenRequest)
}
