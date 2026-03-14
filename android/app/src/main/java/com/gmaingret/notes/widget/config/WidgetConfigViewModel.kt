package com.gmaingret.notes.widget.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.repository.AuthRepository
import com.gmaingret.notes.domain.repository.DocumentRepository
import com.gmaingret.notes.widget.WidgetStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

/**
 * Sealed interface representing all possible states of the widget config screen.
 */
sealed interface ConfigUiState {
    /** Initial load in progress. */
    data object Loading : ConfigUiState

    /** User is not authenticated — show login form. */
    data object NeedsLogin : ConfigUiState

    /** Authenticated and documents loaded — show document picker list. */
    data class DocumentsLoaded(val documents: List<Document>) : ConfigUiState

    /** Auth or document-loading failure — show error message with retry. */
    data class Error(val message: String) : ConfigUiState
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * ViewModel for [WidgetConfigActivity].
 *
 * Responsibilities:
 * 1. Check auth on init (via TokenStore + AuthRepository.refresh).
 * 2. If authenticated, load documents via DocumentRepository.
 * 3. Handle login (email/password and Google SSO).
 * 4. Handle document selection: persist to WidgetStateStore and emit a one-shot
 *    [documentSelectedEvent] for the Activity to finalize with RESULT_OK.
 *
 * Note: This is a @HiltViewModel because WidgetConfigActivity is a
 * @AndroidEntryPoint ComponentActivity — standard Hilt ViewModel injection works here.
 */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
    private val widgetStateStore: WidgetStateStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConfigUiState>(ConfigUiState.Loading)
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    /**
     * One-shot event emitted after [selectDocument] persists the selection.
     * The Activity collects this to call setResult(RESULT_OK) + finish().
     */
    private val _documentSelectedEvent = Channel<Unit>(capacity = Channel.BUFFERED)
    val documentSelectedEvent = _documentSelectedEvent.receiveAsFlow()

    init {
        checkAuthAndLoad()
    }

    // ---------------------------------------------------------------------------
    // Auth check
    // ---------------------------------------------------------------------------

    private fun checkAuthAndLoad() {
        viewModelScope.launch {
            _uiState.value = ConfigUiState.Loading
            val token = tokenStore.getAccessToken()
            if (token == null) {
                _uiState.value = ConfigUiState.NeedsLogin
                return@launch
            }
            // Token exists — validate it by refreshing (also renews the access token)
            val refreshResult = authRepository.refresh()
            if (refreshResult.isFailure) {
                // Token is expired or invalid
                _uiState.value = ConfigUiState.NeedsLogin
                return@launch
            }
            loadDocuments()
        }
    }

    // ---------------------------------------------------------------------------
    // Document loading
    // ---------------------------------------------------------------------------

    private suspend fun loadDocuments() {
        val result = documentRepository.getDocuments()
        _uiState.value = result.fold(
            onSuccess = { documents -> ConfigUiState.DocumentsLoaded(documents) },
            onFailure = { e -> ConfigUiState.Error(e.message ?: "Failed to load documents") }
        )
    }

    // ---------------------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------------------

    /**
     * Email/password login. On success, automatically loads documents.
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = ConfigUiState.Loading
            val result = authRepository.login(email, password)
            result.fold(
                onSuccess = { loadDocuments() },
                onFailure = { e ->
                    _uiState.value = ConfigUiState.Error(e.message ?: "Login failed")
                }
            )
        }
    }

    /**
     * Google SSO login. On success, automatically loads documents.
     */
    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = ConfigUiState.Loading
            val result = authRepository.loginWithGoogle(idToken)
            result.fold(
                onSuccess = { loadDocuments() },
                onFailure = { e ->
                    _uiState.value = ConfigUiState.Error(e.message ?: "Google sign-in failed")
                }
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Document selection
    // ---------------------------------------------------------------------------

    /**
     * Persists the selected document for the given widget and emits a
     * [documentSelectedEvent] so the Activity can finalize the result.
     *
     * @param appWidgetId The widget instance ID from the launcher intent.
     * @param docId The document ID the user tapped.
     */
    fun selectDocument(appWidgetId: Int, docId: String) {
        viewModelScope.launch {
            widgetStateStore.saveDocumentId(appWidgetId, docId)
            _documentSelectedEvent.send(Unit)
        }
    }
}
