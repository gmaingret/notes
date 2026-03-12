package com.gmaingret.notes.presentation.bullet

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.data.model.MoveBulletRequest
import com.gmaingret.notes.data.model.PatchBulletRequest
import com.gmaingret.notes.domain.model.Attachment
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.usecase.AddBookmarkUseCase
import com.gmaingret.notes.domain.usecase.CreateBulletUseCase
import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import com.gmaingret.notes.domain.usecase.FlattenTreeUseCase
import com.gmaingret.notes.domain.usecase.GetAttachmentsUseCase
import com.gmaingret.notes.domain.usecase.GetBookmarksUseCase
import com.gmaingret.notes.domain.usecase.GetBulletsUseCase
import com.gmaingret.notes.domain.usecase.GetUndoStatusUseCase
import com.gmaingret.notes.domain.usecase.IndentBulletUseCase
import com.gmaingret.notes.domain.usecase.MoveBulletUseCase
import com.gmaingret.notes.domain.usecase.OutdentBulletUseCase
import com.gmaingret.notes.domain.usecase.PatchBulletUseCase
import com.gmaingret.notes.domain.usecase.RedoUseCase
import com.gmaingret.notes.domain.usecase.RemoveBookmarkUseCase
import com.gmaingret.notes.domain.usecase.UndoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the bullet tree editor screen.
 *
 * Responsibilities:
 * - Load bullets for a document and maintain [uiState]
 * - Serialize all structural operations through [operationQueue] to prevent race conditions
 * - Expose undo/redo cursor state for toolbar button enabled states
 * - Support zoom-into-bullet mode with breadcrumb navigation
 * - Debounce content and note edits at 500ms to avoid per-keystroke API calls
 * - Apply optimistic updates for instant UI feedback, reverting on failure
 * - Toggle complete/bookmark state with optimistic updates
 * - Lazy-load and expand/collapse attachment lists per bullet
 * - Download attachments via DownloadManager with auth header
 */
@HiltViewModel
class BulletTreeViewModel @Inject constructor(
    application: Application,
    private val getBulletsUseCase: GetBulletsUseCase,
    private val createBulletUseCase: CreateBulletUseCase,
    private val patchBulletUseCase: PatchBulletUseCase,
    private val deleteBulletUseCase: DeleteBulletUseCase,
    private val indentBulletUseCase: IndentBulletUseCase,
    private val outdentBulletUseCase: OutdentBulletUseCase,
    private val moveBulletUseCase: MoveBulletUseCase,
    private val undoUseCase: UndoUseCase,
    private val redoUseCase: RedoUseCase,
    private val getUndoStatusUseCase: GetUndoStatusUseCase,
    private val flattenTreeUseCase: FlattenTreeUseCase,
    private val getBookmarksUseCase: GetBookmarksUseCase,
    private val addBookmarkUseCase: AddBookmarkUseCase,
    private val removeBookmarkUseCase: RemoveBookmarkUseCase,
    private val getAttachmentsUseCase: GetAttachmentsUseCase,
    private val tokenStore: TokenStore
) : AndroidViewModel(application) {

    // -----------------------------------------------------------------------
    // State flows
    // -----------------------------------------------------------------------

    private val _uiState = MutableStateFlow<BulletTreeUiState>(BulletTreeUiState.Loading)
    val uiState: StateFlow<BulletTreeUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _zoomRootId = MutableStateFlow<String?>(null)
    val zoomRootId: StateFlow<String?> = _zoomRootId.asStateFlow()

    private val _breadcrumbPath = MutableStateFlow<List<Bullet>>(emptyList())
    val breadcrumbPath: StateFlow<List<Bullet>> = _breadcrumbPath.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** Bookmarked bullet IDs — updated on document load and after toggle operations. */
    private val _bookmarkedBulletIds = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedBulletIds: StateFlow<Set<String>> = _bookmarkedBulletIds.asStateFlow()

    /** Lazily loaded attachments per bullet ID. Only populated after first expansion. */
    private val _attachments = MutableStateFlow<Map<String, List<Attachment>>>(emptyMap())
    val attachments: StateFlow<Map<String, List<Attachment>>> = _attachments.asStateFlow()

    /** Bullet IDs whose attachment list is currently expanded. */
    private val _expandedAttachments = MutableStateFlow<Set<String>>(emptySet())
    val expandedAttachments: StateFlow<Set<String>> = _expandedAttachments.asStateFlow()

    /** Whether a pull-to-refresh is currently in progress (used by Plan 04). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    private var currentDocumentId: String? = null

    /** Local overrides for content while the user is typing (before debounce fires).
     *  Exposed as StateFlow so the UI can read the latest typed text even before the
     *  debounced PATCH fires and the server state is updated. */
    private val _contentOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val contentOverrides: StateFlow<Map<String, String>> = _contentOverrides.asStateFlow()

    // Backing mutable map — mutations are reflected into _contentOverrides after each change
    private val contentOverridesMap = mutableMapOf<String, String>()

    /** Local overrides for note while the user is typing (before debounce fires). */
    private val noteOverrides = mutableMapOf<String, String>()

    /** Debounce channel for content edits: Pair(bulletId, newContent). */
    private val contentEditFlow = MutableSharedFlow<Pair<String, String>>(replay = 0, extraBufferCapacity = 64)

    /** Debounce channel for note edits: Pair(bulletId, newNote). */
    private val noteEditFlow = MutableSharedFlow<Pair<String, String>>(replay = 0, extraBufferCapacity = 64)

    // -----------------------------------------------------------------------
    // Operation queue — serializes all server calls to prevent race conditions
    // -----------------------------------------------------------------------

    private val operationQueue = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

    init {
        // Drain the operation queue sequentially
        viewModelScope.launch {
            for (op in operationQueue) { op() }
        }

        // Debounced content PATCH: fire 500ms after the last keystroke per bullet.
        // On success, update the bullet's content in the local state so display mode
        // shows the correct text after the override is removed. Without this update,
        // display mode would fall back to the pre-PATCH bullet.content (stale server value).
        viewModelScope.launch {
            contentEditFlow
                .debounce(500)
                .collect { (bulletId, content) ->
                    patchBulletUseCase(bulletId, PatchBulletRequest.updateContent(content))
                        .onSuccess {
                            // Update the bullet's content in local state so bullet.content
                            // reflects the new value before the override is removed.
                            val current = _uiState.value as? BulletTreeUiState.Success
                            if (current != null) {
                                val updatedBullets = current.bullets.map { b ->
                                    if (b.id == bulletId) b.copy(content = content) else b
                                }
                                updateState(updatedBullets)
                            }
                            contentOverridesMap.remove(bulletId)
                            _contentOverrides.value = contentOverridesMap.toMap()
                        }
                }
        }

        // Debounced note PATCH: fire 500ms after the last keystroke per bullet
        viewModelScope.launch {
            noteEditFlow
                .debounce(500)
                .collect { (bulletId, note) ->
                    patchBulletUseCase(bulletId, PatchBulletRequest.updateNote(note))
                }
        }
    }

    private fun enqueue(op: suspend () -> Unit) {
        operationQueue.trySend(op)
    }

    // -----------------------------------------------------------------------
    // Core load methods
    // -----------------------------------------------------------------------

    /**
     * Loads bullets for [documentId], transitioning to Loading then Success/Error.
     * Also fetches initial undo status and bookmarks to set toolbar button states.
     */
    fun loadBullets(documentId: String) {
        currentDocumentId = documentId
        _uiState.value = BulletTreeUiState.Loading
        viewModelScope.launch {
            getBulletsUseCase(documentId).fold(
                onSuccess = { bullets ->
                    updateState(bullets)
                    loadUndoStatus()
                    // Load bookmarks in parallel after bullets are shown
                    getBookmarksUseCase().onSuccess { bookmarks ->
                        _bookmarkedBulletIds.value = bookmarks.map { it.bulletId }.toSet()
                    }
                },
                onFailure = { e ->
                    _uiState.value = BulletTreeUiState.Error(
                        e.message ?: "Failed to load bullets"
                    )
                }
            )
        }
    }

    /**
     * Rebuilds the UI state from a fresh bullet list.
     * Runs [FlattenTreeUseCase] with the current zoom root and computes the breadcrumb trail.
     * Preserves [focusedBulletId] to prevent focus loss during silent server reloads.
     */
    private fun updateState(bullets: List<Bullet>, focusedBulletId: String? = null, focusCursorEnd: Boolean = false) {
        val rootId = _zoomRootId.value
        val flatList = flattenTreeUseCase(bullets, rootId = rootId)

        // Compute breadcrumb path: walk up from zoomRootId to document root
        if (rootId != null) {
            val bulletById = bullets.associateBy { it.id }
            val path = mutableListOf<Bullet>()
            var current = bulletById[rootId]
            while (current != null) {
                path.add(0, current) // prepend to build root-first order
                current = current.parentId?.let { bulletById[it] }
            }
            _breadcrumbPath.value = path
        } else {
            _breadcrumbPath.value = emptyList()
        }

        val currentState = _uiState.value
        val preservedFocusId = focusedBulletId ?: (currentState as? BulletTreeUiState.Success)?.focusedBulletId
        _uiState.value = BulletTreeUiState.Success(
            bullets = bullets,
            flatList = flatList,
            focusedBulletId = preservedFocusId,
            focusCursorEnd = focusCursorEnd
        )
    }

    /**
     * Silently refreshes bullets from the server without showing Loading state.
     * Used after operation failures and undo/redo to restore server-authoritative state.
     */
    private fun reloadFromServer() {
        val docId = currentDocumentId ?: return
        viewModelScope.launch {
            getBulletsUseCase(docId).onSuccess { bullets ->
                updateState(bullets)
            }
        }
    }

    /**
     * Fetches the current undo/redo cursor state and updates the toolbar button flows.
     */
    private fun loadUndoStatus() {
        viewModelScope.launch {
            getUndoStatusUseCase().onSuccess { status ->
                _canUndo.value = status.canUndo
                _canRedo.value = status.canRedo
            }
        }
    }

    /** Returns current bullets from Success state, or empty list if not in Success. */
    private fun currentBullets(): List<Bullet> =
        (_uiState.value as? BulletTreeUiState.Success)?.bullets ?: emptyList()

    /** Returns current flatList from Success state, or empty list if not in Success. */
    private fun currentFlatList() =
        (_uiState.value as? BulletTreeUiState.Success)?.flatList ?: emptyList()

    // -----------------------------------------------------------------------
    // Structural operations
    // -----------------------------------------------------------------------

    /**
     * Creates a new bullet after [afterBulletId] with [parentId] as its parent.
     *
     * Optimistic: inserts a temporary bullet immediately so the UI updates at once.
     * On server success: replaces the temp bullet with the server-returned bullet
     * and focuses the new bullet's real ID.
     * On failure: emits snackbar and reloads from server.
     */
    fun createBullet(afterBulletId: String?, parentId: String?) {
        val docId = currentDocumentId ?: return
        val bullets = currentBullets()

        // Optimistic: insert temp bullet with a temp UUID
        val tempId = "temp-${UUID.randomUUID()}"
        val afterBullet = bullets.find { it.id == afterBulletId }
        val tempPosition = (afterBullet?.position ?: 0.0) + 0.5
        val tempBullet = Bullet(
            id = tempId,
            documentId = docId,
            parentId = parentId,
            content = "",
            position = tempPosition,
            isComplete = false,
            isCollapsed = false,
            note = null
        )
        val optimisticBullets = bullets + tempBullet
        updateState(optimisticBullets, focusedBulletId = tempId)

        enqueue {
            val result = createBulletUseCase(
                CreateBulletRequest(
                    documentId = docId,
                    parentId = parentId,
                    afterId = afterBulletId,
                    content = ""
                )
            )
            result.fold(
                onSuccess = { newBullet ->
                    // Replace temp bullet with real server-returned bullet
                    val currentBullets = currentBullets()
                    val updatedBullets = currentBullets.map { b ->
                        if (b.id == tempId) newBullet else b
                    }
                    updateState(updatedBullets, focusedBulletId = newBullet.id)
                },
                onFailure = {
                    _snackbarMessage.emit("Failed to create bullet")
                    reloadFromServer()
                }
            )
        }
    }

    /**
     * Deletes [bulletId] from the server.
     * On optimistic update, removes the bullet and re-flattens.
     * On failure: emits snackbar and reloads.
     */
    fun deleteBullet(bulletId: String) {
        val bullets = currentBullets()
        val optimisticBullets = bullets.filter { it.id != bulletId }
        updateState(optimisticBullets)

        enqueue {
            deleteBulletUseCase(bulletId).onFailure {
                _snackbarMessage.emit("Failed to delete bullet")
                reloadFromServer()
            }
        }
    }

    /**
     * Indents [bulletId] by making it a child of its previous sibling.
     *
     * Optimistic: re-parents locally and re-flattens.
     * On server success: replaces with authoritative bullet from server.
     * On failure: emits snackbar and reloads.
     */
    fun indentBullet(bulletId: String) {
        val bullets = currentBullets()

        enqueue {
            indentBulletUseCase(bulletId).fold(
                onSuccess = { updatedBullet ->
                    val updatedBullets = bullets.map { b ->
                        if (b.id == bulletId) updatedBullet else b
                    }
                    updateState(updatedBullets)
                },
                onFailure = {
                    _snackbarMessage.emit("Failed to indent bullet")
                    reloadFromServer()
                }
            )
        }
    }

    /**
     * Outdents [bulletId] by moving it to its grandparent's level.
     *
     * Optimistic: re-parents locally and re-flattens.
     * On server success: replaces with authoritative bullet from server.
     * On failure: emits snackbar and reloads.
     */
    fun outdentBullet(bulletId: String) {
        val bullets = currentBullets()

        enqueue {
            outdentBulletUseCase(bulletId).fold(
                onSuccess = { updatedBullet ->
                    val updatedBullets = bullets.map { b ->
                        if (b.id == bulletId) updatedBullet else b
                    }
                    updateState(updatedBullets)
                },
                onFailure = {
                    _snackbarMessage.emit("Failed to outdent bullet")
                    reloadFromServer()
                }
            )
        }
    }

    /**
     * Moves [bulletId] up — to the position before its previous visible sibling,
     * crossing parent boundaries per Dynalist-style spec.
     *
     * If the bullet is the first sibling, it moves up to the parent's previous sibling's
     * last child position.
     */
    fun moveUp(bulletId: String) {
        val flatList = currentFlatList()
        val idx = flatList.indexOfFirst { it.bullet.id == bulletId }
        if (idx <= 0) return // already first, nothing to do

        val bullet = flatList[idx].bullet
        val prevFlatBullet = flatList[idx - 1]

        // Compute target: place current bullet before prevFlatBullet in the flat list
        // afterId = the bullet BEFORE prevFlatBullet (if any and same parent), else null
        val targetAfterIndex = idx - 2
        val targetAfter = if (targetAfterIndex >= 0) {
            val candidate = flatList[targetAfterIndex]
            // Only use as afterId if it's a sibling of prevFlatBullet
            if (candidate.bullet.parentId == prevFlatBullet.bullet.parentId) {
                candidate.bullet.id
            } else null
        } else null

        val newParentId = prevFlatBullet.bullet.parentId

        enqueue {
            moveBulletUseCase(
                bulletId,
                MoveBulletRequest(newParentId = newParentId, afterId = targetAfter)
            ).fold(
                onSuccess = { updatedBullet ->
                    val bullets = currentBullets()
                    val updatedBullets = bullets.map { b ->
                        if (b.id == bulletId) updatedBullet else b
                    }
                    updateState(updatedBullets)
                },
                onFailure = {
                    _snackbarMessage.emit("Failed to move bullet up")
                    reloadFromServer()
                }
            )
        }
    }

    /**
     * Moves [bulletId] down — to the position after its next visible sibling,
     * crossing parent boundaries per Dynalist-style spec.
     *
     * If the bullet is the last sibling, it moves down to the parent's next sibling's
     * first child position.
     */
    fun moveDown(bulletId: String) {
        val flatList = currentFlatList()
        val idx = flatList.indexOfFirst { it.bullet.id == bulletId }
        if (idx < 0 || idx >= flatList.size - 1) return // already last, nothing to do

        val bullet = flatList[idx].bullet
        val nextFlatBullet = flatList[idx + 1]

        // Place current bullet after nextFlatBullet
        val newParentId = nextFlatBullet.bullet.parentId
        val afterId = nextFlatBullet.bullet.id

        enqueue {
            moveBulletUseCase(
                bulletId,
                MoveBulletRequest(newParentId = newParentId, afterId = afterId)
            ).fold(
                onSuccess = { updatedBullet ->
                    val bullets = currentBullets()
                    val updatedBullets = bullets.map { b ->
                        if (b.id == bulletId) updatedBullet else b
                    }
                    updateState(updatedBullets)
                },
                onFailure = {
                    _snackbarMessage.emit("Failed to move bullet down")
                    reloadFromServer()
                }
            )
        }
    }

    /**
     * Toggles the collapsed state of [bulletId].
     *
     * Optimistic: flips isCollapsed in the local bullet list and re-flattens immediately
     * so children appear/disappear before the API call completes.
     * PATCHes the new isCollapsed value to the server.
     * On failure: emits snackbar and reloads.
     */
    fun toggleCollapse(bulletId: String) {
        val bullets = currentBullets()
        val target = bullets.find { it.id == bulletId } ?: return
        val newCollapsed = !target.isCollapsed

        // Optimistic update
        val optimisticBullets = bullets.map { b ->
            if (b.id == bulletId) b.copy(isCollapsed = newCollapsed) else b
        }
        updateState(optimisticBullets)

        enqueue {
            patchBulletUseCase(bulletId, PatchBulletRequest.updateIsCollapsed(newCollapsed))
                .onFailure {
                    _snackbarMessage.emit("Failed to toggle collapse")
                    reloadFromServer()
                }
        }
    }

    /**
     * Toggles the complete state of [bulletId].
     *
     * Optimistic: flips isComplete in the local bullet list immediately.
     * PATCHes the new isComplete value to the server.
     * On failure: emits snackbar and reloads from server to revert.
     */
    fun toggleComplete(bulletId: String) {
        val bullets = currentBullets()
        val target = bullets.find { it.id == bulletId } ?: return
        val newComplete = !target.isComplete

        // Optimistic update
        val optimisticBullets = bullets.map { b ->
            if (b.id == bulletId) b.copy(isComplete = newComplete) else b
        }
        updateState(optimisticBullets)

        enqueue {
            patchBulletUseCase(bulletId, PatchBulletRequest.updateIsComplete(newComplete))
                .onFailure {
                    _snackbarMessage.emit("Failed to update bullet")
                    reloadFromServer()
                }
        }
    }

    /**
     * Toggles the bookmark state of [bulletId].
     *
     * Optimistic: updates [_bookmarkedBulletIds] immediately.
     * Calls add/remove bookmark API on server.
     * On failure: emits snackbar and reloads bookmarks.
     */
    fun toggleBookmark(bulletId: String) {
        val isCurrentlyBookmarked = bulletId in _bookmarkedBulletIds.value
        // Optimistic update
        _bookmarkedBulletIds.value = if (isCurrentlyBookmarked) {
            _bookmarkedBulletIds.value - bulletId
        } else {
            _bookmarkedBulletIds.value + bulletId
        }

        enqueue {
            val result = if (isCurrentlyBookmarked) {
                removeBookmarkUseCase(bulletId)
            } else {
                addBookmarkUseCase(bulletId)
            }
            result.onFailure {
                _snackbarMessage.emit("Failed to update bookmark")
                // Revert bookmark state
                getBookmarksUseCase().onSuccess { bookmarks ->
                    _bookmarkedBulletIds.value = bookmarks.map { it.bulletId }.toSet()
                }
            }
        }
    }

    /**
     * Toggles the attachment expansion for [bulletId].
     *
     * If collapsing: removes from expanded set.
     * If expanding: adds to expanded set and lazy-loads attachments if not already loaded.
     */
    fun toggleAttachmentExpansion(bulletId: String) {
        val current = _expandedAttachments.value
        if (bulletId in current) {
            _expandedAttachments.value = current - bulletId
        } else {
            _expandedAttachments.value = current + bulletId
            if (bulletId !in _attachments.value) {
                loadAttachments(bulletId)
            }
        }
    }

    /**
     * Loads attachments for [bulletId] from the server and caches in [_attachments].
     */
    private fun loadAttachments(bulletId: String) {
        viewModelScope.launch {
            getAttachmentsUseCase(bulletId).onSuccess { list ->
                _attachments.value = _attachments.value + (bulletId to list)
            }
        }
    }

    /**
     * Downloads [attachment] via DownloadManager with auth header.
     *
     * Retrieves the access token from [TokenStore] (suspend), then enqueues the download.
     * The DownloadManager handles progress notification and filesystem placement.
     */
    fun downloadAttachment(attachment: Attachment) {
        viewModelScope.launch {
            val token = tokenStore.getAccessToken() ?: return@launch
            val request = DownloadManager.Request(Uri.parse(attachment.downloadUrl))
                .setTitle(attachment.filename)
                .setDescription("Downloading...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, attachment.filename)
                .addRequestHeader("Authorization", "Bearer $token")
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }
    }

    /**
     * Refreshes bullets from server, setting [_isRefreshing] appropriately.
     * Used by pull-to-refresh (Plan 04).
     */
    fun refresh() {
        val docId = currentDocumentId ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                getBulletsUseCase(docId).onSuccess { bullets ->
                    updateState(bullets)
                    getBookmarksUseCase().onSuccess { bookmarks ->
                        _bookmarkedBulletIds.value = bookmarks.map { it.bulletId }.toSet()
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Records [content] for [bulletId] in the local override map and emits to the debounce flow.
     * The actual PATCH fires 500ms after the last call for a given bullet ID.
     * The override is cleared once the debounced PATCH fires so the UI reverts to server state.
     */
    fun updateContent(bulletId: String, content: String) {
        contentOverridesMap[bulletId] = content
        _contentOverrides.value = contentOverridesMap.toMap()
        viewModelScope.launch {
            contentEditFlow.emit(Pair(bulletId, content))
        }
    }

    /**
     * Records [note] for [bulletId] in the local override map and emits to the debounce flow.
     * The actual PATCH fires 500ms after the last call for a given bullet ID.
     */
    fun saveNote(bulletId: String, note: String) {
        noteOverrides[bulletId] = note
        viewModelScope.launch {
            noteEditFlow.emit(Pair(bulletId, note))
        }
    }

    /**
     * Calls the server undo endpoint, updates canUndo/canRedo from the response,
     * then reloads the full tree to reflect the reverted state.
     */
    fun undo() {
        enqueue {
            undoUseCase().fold(
                onSuccess = { status ->
                    _canUndo.value = status.canUndo
                    _canRedo.value = status.canRedo
                    reloadFromServer()
                },
                onFailure = {
                    _snackbarMessage.emit("Undo failed")
                }
            )
        }
    }

    /**
     * Calls the server redo endpoint, updates canUndo/canRedo from the response,
     * then reloads the full tree to reflect the re-applied state.
     */
    fun redo() {
        enqueue {
            redoUseCase().fold(
                onSuccess = { status ->
                    _canUndo.value = status.canUndo
                    _canRedo.value = status.canRedo
                    reloadFromServer()
                },
                onFailure = {
                    _snackbarMessage.emit("Redo failed")
                }
            )
        }
    }

    /**
     * Updates [focusedBulletId] in the current Success state.
     * No-op if state is not Success.
     */
    fun setFocusedBullet(bulletId: String?) {
        val current = _uiState.value as? BulletTreeUiState.Success ?: return
        _uiState.value = current.copy(focusedBulletId = bulletId, focusCursorEnd = false)
    }

    /**
     * Zooms into [bulletId]'s subtree. Pass null to zoom back to document root.
     *
     * Sets [_zoomRootId] and recomputes the breadcrumb path by walking up the parentId
     * chain from [bulletId] to the document root.
     */
    fun zoomTo(bulletId: String?) {
        _zoomRootId.value = bulletId
        val bullets = currentBullets()
        updateState(bullets)
    }

    /**
     * Handles Enter key pressed on an empty bullet.
     *
     * - If bullet has a parent: outdents it (same as pressing Shift+Tab on empty)
     * - If root-level: clears focus (no further action)
     */
    fun enterOnEmpty(bulletId: String) {
        val bullets = currentBullets()
        val bullet = bullets.find { it.id == bulletId } ?: return
        if (bullet.parentId != null) {
            outdentBullet(bulletId)
        } else {
            setFocusedBullet(null)
        }
    }

    /**
     * Handles Backspace key pressed on an empty bullet.
     *
     * Finds the previous visible bullet in the flat list. If there is none, does nothing
     * (cannot delete the first root bullet). Otherwise:
     * - Reparents any children of [bulletId] to [bulletId]'s own parent (same sibling group)
     * - Removes [bulletId] from the local list
     * - Sets focus to the previous bullet with [focusCursorEnd] = true
     * - Enqueues DELETE /api/bullets/:id
     */
    fun backspaceOnEmpty(bulletId: String) {
        val flatList = currentFlatList()
        val idx = flatList.indexOfFirst { it.bullet.id == bulletId }
        if (idx <= 0) return // first bullet — nothing to backspace into

        val prevBullet = flatList[idx - 1].bullet
        val targetBullet = flatList[idx].bullet

        val bullets = currentBullets()

        // Reparent children of deleted bullet to deleted bullet's own parent
        val deletedParentId = targetBullet.parentId
        val optimisticBullets = bullets
            .filter { it.id != bulletId }
            .map { b ->
                if (b.parentId == bulletId) b.copy(parentId = deletedParentId) else b
            }

        updateState(optimisticBullets, focusedBulletId = prevBullet.id, focusCursorEnd = true)

        enqueue {
            deleteBulletUseCase(bulletId).onFailure {
                _snackbarMessage.emit("Failed to delete bullet")
                reloadFromServer()
            }
        }
    }

    /**
     * Reorders the flat list optimistically for drag-and-drop preview.
     * Does NOT fire any API call — call [commitBulletMove] when the drag ends.
     */
    fun moveBulletLocally(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value as? BulletTreeUiState.Success ?: return
        if (fromIndex < 0 || fromIndex >= current.flatList.size) return
        if (toIndex < 0 || toIndex >= current.flatList.size) return
        val newFlatList = current.flatList.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
        _uiState.value = current.copy(flatList = newFlatList)
    }

    /**
     * Emits a snackbar message from outside the ViewModel (e.g., cycle prevention in UI).
     * Launches in viewModelScope so callers don't need a coroutine context.
     */
    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    /**
     * Fires [MoveBulletUseCase] after a drag ends.
     *
     * On success: updates the server-authoritative bullet in the local list and re-flattens.
     * On failure: emits snackbar and reloads full list from server.
     */
    fun commitBulletMove(bulletId: String, newParentId: String?, afterId: String?) {
        val bullets = currentBullets()

        enqueue {
            moveBulletUseCase(
                bulletId,
                MoveBulletRequest(newParentId = newParentId, afterId = afterId)
            ).fold(
                onSuccess = { updatedBullet ->
                    val updatedBullets = bullets.map { b ->
                        if (b.id == bulletId) updatedBullet else b
                    }
                    updateState(updatedBullets)
                },
                onFailure = {
                    _snackbarMessage.emit("Failed to move bullet")
                    reloadFromServer()
                }
            )
        }
    }
}
