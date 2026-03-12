package com.gmaingret.notes.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    init {
        viewModelScope.launch {
            _userEmail.value = tokenStore.getUserEmail() ?: ""
        }
    }

    /**
     * Logs the user out:
     * 1. Calls LogoutUseCase which hits POST /api/auth/logout (clears server cookie)
     *    and then clears local DataStore tokens via TokenStore.clearAll().
     * 2. Invokes [onComplete] so the caller can navigate back to AuthScreen.
     */
    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase()
            onComplete()
        }
    }
}
