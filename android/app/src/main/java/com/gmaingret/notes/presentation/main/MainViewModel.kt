package com.gmaingret.notes.presentation.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.domain.usecase.CreateDocumentUseCase
import com.gmaingret.notes.domain.usecase.DeleteDocumentUseCase
import com.gmaingret.notes.domain.usecase.ExportAllDocumentsUseCase
import com.gmaingret.notes.domain.usecase.ExportDocumentUseCase
import com.gmaingret.notes.domain.usecase.GetDocumentsUseCase
import com.gmaingret.notes.domain.usecase.ImportDocumentUseCase
import com.gmaingret.notes.domain.usecase.LogoutUseCase
import com.gmaingret.notes.domain.usecase.OpenDocumentUseCase
import com.gmaingret.notes.domain.usecase.RenameDocumentUseCase
import com.gmaingret.notes.domain.usecase.ReorderDocumentUseCase
import androidx.glance.appwidget.updateAll
import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.NotesWidget
import com.gmaingret.notes.widget.WidgetStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logoutUseCase: LogoutUseCase,
    private val tokenStore: TokenStore,
    private val widgetStateStore: WidgetStateStore,
    private val getDocumentsUseCase: GetDocumentsUseCase,
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val reorderDocumentUseCase: ReorderDocumentUseCase,
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val importDocumentUseCase: ImportDocumentUseCase,
    private val exportDocumentUseCase: ExportDocumentUseCase,
    private val exportAllDocumentsUseCase: ExportAllDocumentsUseCase
) : ViewModel() {

    // -----------------------------------------------------------------------
    // Existing state (unchanged)
    // -----------------------------------------------------------------------

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    // -----------------------------------------------------------------------
    // Document state
    // -----------------------------------------------------------------------

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    /** Emits (filename, bytes) when a file is ready to be saved/shared. */
    data class FileExport(val filename: String, val bytes: ByteArray)
    private val _fileExport = MutableSharedFlow<FileExport>()
    val fileExport: SharedFlow<FileExport> = _fileExport.asSharedFlow()

    /** Timestamp of last successful document fetch — used to debounce rapid refreshes. */
    private var lastFetchMillis = 0L
    private companion object { const val REFRESH_DEBOUNCE_MS = 5_000L }

    // -----------------------------------------------------------------------
    // Init
    // -----------------------------------------------------------------------

    init {
        viewModelScope.launch {
            _userEmail.value = tokenStore.getUserEmail() ?: ""
        }
        viewModelScope.launch {
            tokenStore.themeModeFlow().collect { _themeMode.value = it }
        }
        loadDocuments()
    }

    // -----------------------------------------------------------------------
    // Logout (unchanged)
    // -----------------------------------------------------------------------

    /**
     * Logs the user out:
     * 1. Calls LogoutUseCase which hits POST /api/auth/logout/token (revokes server token)
     *    and then clears local DataStore tokens via TokenStore.clearAll().
     * 2. Writes SESSION_EXPIRED to WidgetStateStore so the widget reflects auth state immediately.
     * 3. Triggers updateAll() to cause provideGlance to re-render from the updated cache.
     * 4. Invokes [onComplete] so the caller can navigate back to AuthScreen.
     */
    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase()
            // Update widget to show SESSION_EXPIRED immediately after logout
            try {
                widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
                NotesWidget.pushStateToGlance(context)
            } catch (_: Exception) {
                // Widget update is best-effort — do not block logout navigation
            }
            onComplete()
        }
    }

    // -----------------------------------------------------------------------
    // Document operations
    // -----------------------------------------------------------------------

    /**
     * Fetches the document list and performs cold-start open logic:
     * - Empty list → Empty state
     * - Non-empty list → open lastDocId (or first if not found/null) → Success state
     */
    fun loadDocuments() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            lastFetchMillis = System.currentTimeMillis()
            val result = getDocumentsUseCase()
            result.fold(
                onSuccess = { documents ->
                    if (documents.isEmpty()) {
                        _uiState.value = MainUiState.Empty
                    } else {
                        val lastDocId = openDocumentUseCase.getLastDocId()
                        val docToOpen = if (lastDocId != null && documents.any { it.id == lastDocId }) {
                            documents.first { it.id == lastDocId }
                        } else {
                            documents.first()
                        }
                        openDocumentUseCase(docToOpen.id)
                        _uiState.value = MainUiState.Success(
                            documents = documents,
                            openDocumentId = docToOpen.id
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = MainUiState.Error(e.message ?: "Failed to load documents")
                }
            )
        }
    }

    /**
     * Opens a document: updates openDocumentId in state and calls OpenDocumentUseCase
     * (fire-and-forget — use case handles persistence and API call internally).
     */
    fun openDocument(docId: String) {
        val current = _uiState.value as? MainUiState.Success ?: return
        _uiState.value = current.copy(openDocumentId = docId)
        viewModelScope.launch {
            openDocumentUseCase(docId)
        }
    }

    /**
     * Creates a new "Untitled" document, appends it to the list, and activates
     * inline editing so the user can immediately type a title.
     */
    fun createDocument() {
        viewModelScope.launch {
            val result = createDocumentUseCase("Untitled")
            result.onSuccess { newDoc ->
                val current = _uiState.value
                val currentDocs = if (current is MainUiState.Success) current.documents else emptyList()
                _uiState.value = MainUiState.Success(
                    documents = currentDocs + newDoc,
                    openDocumentId = newDoc.id,
                    inlineEditingDocId = newDoc.id
                )
            }
        }
    }

    /**
     * Submits a renamed title, updates the document in the list, and clears inline editing.
     */
    fun submitRename(id: String, title: String) {
        viewModelScope.launch {
            val result = renameDocumentUseCase(id, title)
            result.onSuccess { updatedDoc ->
                val current = _uiState.value as? MainUiState.Success ?: return@onSuccess
                val updatedList = current.documents.map { if (it.id == id) updatedDoc else it }
                _uiState.value = current.copy(
                    documents = updatedList,
                    inlineEditingDocId = null
                )
            }
        }
    }

    /**
     * Starts inline editing for the given document (sets inlineEditingDocId).
     */
    fun startRename(docId: String) {
        val current = _uiState.value as? MainUiState.Success ?: return
        _uiState.value = current.copy(inlineEditingDocId = docId)
    }

    /**
     * Cancels the inline rename. The document being edited stays in the list
     * (user can rename later).
     */
    fun cancelRename() {
        val current = _uiState.value as? MainUiState.Success ?: return
        _uiState.value = current.copy(inlineEditingDocId = null)
    }

    /**
     * Deletes a document and auto-opens the next adjacent document:
     * - Next document if deleted doc was not last
     * - Previous document if deleted doc was last
     * - Empty state if list is now empty
     */
    fun deleteDocument(id: String) {
        viewModelScope.launch {
            val result = deleteDocumentUseCase(id)
            result.onSuccess {
                val current = _uiState.value as? MainUiState.Success ?: return@onSuccess
                val oldList = current.documents
                val deletedIndex = oldList.indexOfFirst { it.id == id }
                val newList = oldList.filter { it.id != id }

                if (newList.isEmpty()) {
                    _uiState.value = MainUiState.Empty
                    return@onSuccess
                }

                // Determine which doc to open next
                val wasOpen = current.openDocumentId == id
                val newOpenId = if (wasOpen) {
                    // Try next; fall back to previous
                    val nextIndex = deletedIndex.coerceAtMost(newList.size - 1)
                    newList[nextIndex].id
                } else {
                    current.openDocumentId
                }

                if (wasOpen) {
                    openDocumentUseCase(newOpenId ?: newList.first().id)
                }

                _uiState.value = MainUiState.Success(
                    documents = newList,
                    openDocumentId = newOpenId
                )
            }
        }
    }

    /**
     * Reorders the document list in-memory for optimistic drag UI.
     * Call [commitReorder] after drag ends to persist the new order.
     */
    fun moveDocumentLocally(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value as? MainUiState.Success ?: return
        val newList = current.documents.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        _uiState.value = current.copy(documents = newList)
    }

    /**
     * Persists the current in-memory order by calling [ReorderDocumentUseCase] for [docId].
     *
     * afterId = the document immediately before [docId] in the current list (null if first).
     * On failure: reloads from API (revert) and emits a snackbar message.
     */
    fun commitReorder(docId: String) {
        viewModelScope.launch {
            val current = _uiState.value as? MainUiState.Success ?: return@launch
            val currentIndex = current.documents.indexOfFirst { it.id == docId }
            if (currentIndex < 0) return@launch

            val afterId = if (currentIndex == 0) null else current.documents[currentIndex - 1].id
            val result = reorderDocumentUseCase(docId, afterId)

            result.onFailure {
                // Revert: reload from API
                _snackbarMessage.emit("Reorder failed. Restoring original order.")
                val reloadResult = getDocumentsUseCase()
                reloadResult.onSuccess { docs ->
                    _uiState.value = current.copy(documents = docs)
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Navigation helpers (Plan 03 — search + bookmarks)
    // -----------------------------------------------------------------------

    /**
     * Switches main content area to the Bookmarks screen.
     * Clears tags and any pending scroll.
     */
    fun showBookmarks() {
        val current = _uiState.value as? MainUiState.Success ?: return
        _uiState.value = current.copy(showBookmarks = true, showTags = false, pendingScrollToBulletId = null)
    }

    /**
     * Switches main content area to the Tags browser screen.
     * Clears bookmarks and any pending scroll.
     */
    fun showTags() {
        val current = _uiState.value as? MainUiState.Success ?: return
        _uiState.value = current.copy(showTags = true, showBookmarks = false, pendingScrollToBulletId = null)
    }

    /**
     * Navigates to a specific bullet in a document (from search or bookmark tap).
     * Opens the document, hides bookmarks, and sets pendingScrollToBulletId so
     * BulletTreeScreen can animate-scroll to the bullet.
     */
    fun navigateToBullet(documentId: String, bulletId: String) {
        val current = _uiState.value as? MainUiState.Success ?: return
        _uiState.value = current.copy(
            openDocumentId = documentId,
            showBookmarks = false,
            showTags = false,
            pendingScrollToBulletId = bulletId
        )
        viewModelScope.launch {
            openDocumentUseCase(documentId)
        }
    }

    /**
     * Called by BulletTreeScreen after it has scrolled to the pending bullet.
     * Clears the pending scroll ID to prevent re-triggering.
     */
    fun clearPendingScroll() {
        val current = _uiState.value as? MainUiState.Success ?: return
        _uiState.value = current.copy(pendingScrollToBulletId = null)
    }

    /**
     * Refreshes the document list (e.g. when the drawer opens).
     * Preserves the currently open document ID; if it's no longer in the list,
     * falls back to the first document.
     */
    fun refreshDocuments() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (now - lastFetchMillis < REFRESH_DEBOUNCE_MS) {
                return@launch
            }
            _isRefreshing.value = true
            lastFetchMillis = now
            val current = _uiState.value as? MainUiState.Success
            val result = getDocumentsUseCase()
            result.fold(
                onSuccess = { docs ->
                    if (docs.isEmpty()) {
                        _uiState.value = MainUiState.Empty
                    } else {
                        val currentOpenId = current?.openDocumentId
                        val newOpenId = if (currentOpenId != null && docs.any { it.id == currentOpenId }) {
                            currentOpenId
                        } else {
                            docs.first().id
                        }
                        _uiState.value = MainUiState.Success(
                            documents = docs,
                            openDocumentId = newOpenId,
                            inlineEditingDocId = current?.inlineEditingDocId
                        )
                    }
                    _isRefreshing.value = false
                },
                onFailure = {
                    _isRefreshing.value = false
                }
            )
        }
    }

    fun importDocument(markdown: String) {
        viewModelScope.launch {
            importDocumentUseCase(markdown).fold(
                onSuccess = { doc ->
                    loadDocuments()
                    openDocument(doc.id)
                },
                onFailure = { /* silently fail — could add snackbar later */ }
            )
        }
    }

    fun exportDocument(id: String) {
        viewModelScope.launch {
            exportDocumentUseCase(id).fold(
                onSuccess = { (filename, bytes) ->
                    _fileExport.emit(FileExport(filename, bytes))
                },
                onFailure = {
                    _snackbarMessage.emit("Export failed")
                }
            )
        }
    }

    fun exportAllDocuments() {
        viewModelScope.launch {
            exportAllDocumentsUseCase().fold(
                onSuccess = { bytes ->
                    _fileExport.emit(FileExport("notes-export.zip", bytes))
                },
                onFailure = {
                    _snackbarMessage.emit("Export failed")
                }
            )
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch {
            tokenStore.saveThemeMode(mode)
        }
    }
}
