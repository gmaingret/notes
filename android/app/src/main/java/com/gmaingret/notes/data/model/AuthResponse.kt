package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response from /api/auth/register, /api/auth/login, /api/auth/google/token.
 * Shape: { accessToken, refreshToken, user: { id, email } }
 */
data class AuthResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String,
    @SerializedName("user")
    val user: UserDto
)

data class UserDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("email")
    val email: String
)
