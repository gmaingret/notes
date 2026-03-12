package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response from POST /api/auth/refresh.
 * Shape: { accessToken: string }
 */
data class RefreshResponse(
    @SerializedName("accessToken")
    val accessToken: String
)
