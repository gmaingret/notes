package com.gmaingret.notes.presentation.auth

data class AuthUiState(
    val selectedTab: AuthTab = AuthTab.LOGIN,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isGoogleSignInAvailable: Boolean = false,
    val snackbarMessage: String? = null
)

enum class AuthTab { LOGIN, REGISTER }
