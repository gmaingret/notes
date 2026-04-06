package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response from POST /api/auth/refresh/token.
 * Shape: { accessToken }
 *
 * The refresh token is NOT rotated — it stays valid for its full 7-day lifetime.
 * Only a new access token is returned.
 */
data class RefreshResponse(
    @SerializedName("accessToken")
    val accessToken: String
)
