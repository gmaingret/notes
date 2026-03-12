package com.gmaingret.notes.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.domain.model.AuthState
import com.gmaingret.notes.domain.usecase.CheckAuthUseCase
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Splash screen ViewModel that determines the initial navigation destination.
 *
 * Cold start flow:
 * 1. authState starts as CHECKING — MainActivity uses setKeepOnScreenCondition
 *    to hold the splash screen until this is no longer CHECKING.
 * 2. CheckAuthUseCase calls POST /api/auth/refresh to validate the refreshToken cookie.
 *    - Success -> AUTHENTICATED -> MainActivity routes to MainRoute
 *    - Failure (401, invalid) -> UNAUTHENTICATED -> MainActivity routes to AuthRoute()
 *    - Network error -> UNAUTHENTICATED + coldStartNetworkError=true -> AuthRoute(showNetworkError=true)
 *
 * CRITICAL: Always sets a terminal state (AUTHENTICATED or UNAUTHENTICATED) so the
 * splash screen never hangs on CHECKING (research Pitfall 5).
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val checkAuthUseCase: CheckAuthUseCase
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState.CHECKING)
    val authState = _authState.asStateFlow()

    private val _coldStartNetworkError = MutableStateFlow(false)
    val coldStartNetworkError = _coldStartNetworkError.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                Log.d("SplashVM", "Checking auth...")
                val isAuthenticated = checkAuthUseCase()
                Log.d("SplashVM", "Auth result: $isAuthenticated")
                _authState.value = if (isAuthenticated) {
                    AuthState.AUTHENTICATED
                } else {
                    AuthState.UNAUTHENTICATED
                }
            } catch (e: Exception) {
                Log.e("SplashVM", "Auth check failed", e)
                // Network failure or other error during cold-start refresh
                _coldStartNetworkError.value = true
                _authState.value = AuthState.UNAUTHENTICATED
            }
        }
    }
}
