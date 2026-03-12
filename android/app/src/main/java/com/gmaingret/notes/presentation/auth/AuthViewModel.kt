package com.gmaingret.notes.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.domain.usecase.GoogleSignInUseCase
import com.gmaingret.notes.domain.usecase.LoginUseCase
import com.gmaingret.notes.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val googleSignInUseCase: GoogleSignInUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, emailError = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null) }
    }

    fun onTabSelected(tab: AuthTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                emailError = null,
                passwordError = null,
                snackbarMessage = null
            )
        }
    }

    fun onPasswordVisibilityToggle() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onSnackbarDismissed() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Called by AuthScreen when cold-start network failure is detected (showNetworkErrorOnStart = true).
     * Sets the snackbar message so it appears immediately via LaunchedEffect.
     */
    fun showNetworkError() {
        _uiState.update { it.copy(snackbarMessage = "Can't reach server. Check your connection.") }
    }

    fun onSubmit(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Client-side validation (submit-only per locked decision)
        var emailError: String? = null
        var passwordError: String? = null

        if (state.email.isBlank() || !emailRegex.matches(state.email)) {
            emailError = "Enter a valid email"
        }
        if (state.selectedTab == AuthTab.REGISTER) {
            if (state.password.length < 8) {
                passwordError = "Password must be at least 8 characters"
            }
        } else {
            if (state.password.isEmpty()) {
                passwordError = "Password is required"
            }
        }

        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = if (state.selectedTab == AuthTab.LOGIN) {
                loginUseCase(state.email, state.password)
            } else {
                registerUseCase(state.email, state.password)
            }

            result.fold(
                onSuccess = { _ ->
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                },
                onFailure = { throwable ->
                    handleAuthError(throwable)
                    _uiState.update { it.copy(isLoading = false) }
                }
            )
        }
    }

    /**
     * Called from AuthScreen LaunchedEffect to update Credential Manager availability.
     * ViewModel must not hold Activity context, so the check is done in the composable
     * and the result is passed here.
     */
    fun setGoogleSignInAvailability(available: Boolean) {
        _uiState.update { it.copy(isGoogleSignInAvailable = available) }
    }

    /**
     * Triggers Google Sign-In via Credential Manager two-step flow.
     * Context is obtained from LocalContext.current in AuthScreen (not stored in ViewModel).
     * On success: invokes [onSuccess] callback.
     * On failure: shows Snackbar "Google sign-in failed. Please try again." (per locked decision).
     */
    fun onGoogleSignIn(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = googleSignInUseCase(context)

            result.fold(
                onSuccess = { _ ->
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                },
                onFailure = { _ ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            snackbarMessage = "Google sign-in failed. Please try again."
                        )
                    }
                }
            )
        }
    }

    /**
     * Parses server errors into inline field errors or snackbar messages.
     *
     * Error mapping:
     * - HTTP 409 with field "email" -> emailError "Email already registered"
     * - HTTP 401 "Invalid credentials" -> passwordError "Wrong email or password"
     * - Network/IO error -> snackbarMessage
     */
    private fun handleAuthError(throwable: Throwable) {
        when (throwable) {
            is HttpException -> {
                val code = throwable.code()
                val bodyString = throwable.response()?.errorBody()?.string() ?: ""

                when (code) {
                    409 -> {
                        // Email conflict: { field: "email", message: "Email already registered" }
                        val (field, message) = parseFieldMessage(bodyString)
                        if (field == "email") {
                            _uiState.update {
                                it.copy(emailError = message.ifBlank { "Email already registered" })
                            }
                        } else {
                            _uiState.update {
                                it.copy(snackbarMessage = message.ifBlank { "An error occurred. Please try again." })
                            }
                        }
                    }
                    401 -> {
                        // Invalid credentials or wrong password
                        val (_, message) = parseFieldMessage(bodyString)
                        val serverMsg = message.ifBlank { "" }
                        if (serverMsg.contains("Invalid credentials", ignoreCase = true) ||
                            serverMsg.contains("password", ignoreCase = true) ||
                            serverMsg.isEmpty()
                        ) {
                            _uiState.update { it.copy(passwordError = "Wrong email or password") }
                        } else {
                            _uiState.update { it.copy(snackbarMessage = serverMsg) }
                        }
                    }
                    else -> {
                        val (_, message) = parseFieldMessage(bodyString)
                        _uiState.update {
                            it.copy(snackbarMessage = message.ifBlank { "An error occurred. Please try again." })
                        }
                    }
                }
            }
            is IOException -> {
                _uiState.update {
                    it.copy(snackbarMessage = "Can't reach server. Check your connection.")
                }
            }
            else -> {
                _uiState.update {
                    it.copy(snackbarMessage = "An error occurred. Please try again.")
                }
            }
        }
    }

    /**
     * Attempts to parse { field, message } or { error, message } from a JSON error body.
     * Returns Pair("", "") on any parse failure.
     */
    private fun parseFieldMessage(bodyString: String): Pair<String, String> {
        return try {
            val json = JSONObject(bodyString)
            val field = json.optString("field", "")
            val message = json.optString("message", json.optString("error", ""))
            Pair(field, message)
        } catch (_: Exception) {
            Pair("", "")
        }
    }
}
