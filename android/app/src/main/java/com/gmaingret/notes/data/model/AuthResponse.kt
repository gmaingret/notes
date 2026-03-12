package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response from /api/auth/register, /api/auth/login, /api/auth/google/token.
 * Shape: { accessToken: string, user: { id: string, email: string } }
 */
data class AuthResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("user")
    val user: UserDto
)

/**
 * User shape returned by auth endpoints.
 */
data class UserDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("email")
    val email: String
)
