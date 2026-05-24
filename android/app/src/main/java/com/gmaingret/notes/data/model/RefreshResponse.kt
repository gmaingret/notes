package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response from POST /api/auth/refresh/token.
 * Shape: { accessToken, refreshToken }
 *
 * The refresh token is rotated on every call. Clients MUST persist the returned
 * refreshToken — the one in the request body is revoked immediately (with a
 * short grace window for concurrent retries).
 */
data class RefreshResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String
)
