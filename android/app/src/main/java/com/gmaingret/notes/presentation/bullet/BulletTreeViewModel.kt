package com.gmaingret.notes.presentation.bullet

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
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
import com.gmaingret.notes.domain.usecase.DeleteAttachmentUseCase
import com.gmaingret.notes.domain.usecase.UploadAttachmentUseCase
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
import kotlinx.coroutines.runBlocking
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
    private val uploadAttachmentUseCase: UploadAttachmentUseCase,
    private val deleteAttachmentUseCase: DeleteAttachmentUseCase,
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

    /**
     * When non-null, the UI should open a file picker for this bullet ID.
     * Cleared after the picker is launched (set to null by the UI).
     */
    private val _pendingAttachmentBulletId = MutableStateFlow<String?>(null)
    val pendingAttachmentBulletId: StateFlow<String?> = _pendingAttachmentBulletId.asStateFlow()

    /** Whether a pull-to-refresh is currently in progress (used by Plan 04). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Whether completed bullets are currently hidden from the flat list. */
    private val _hideCompleted = MutableStateFlow(false)
    val hideCompleted: StateFlow<Boolean> = _hideCompleted.asStateFlow()

    /** Scroll target set directly by MainScreen (bypasses Crossfade parameter passing). */
    private val _scrollTarget = MutableStateFlow<String?>(null)
    val scrollTarget: StateFlow<String?> = _scrollTarget.asStateFlow()

    /**
     * Set true while the user is dragging a bullet (between onDragStarted and commitBulletMove).
     * While true, [updateState] preserves the current flatList instead of rebuilding from
     * the bullet tree — this prevents content-debounce or other state updates from snapping
     * the visually-reordered list back to pre-drag positions.
     */
    private val _isDragInProgress = MutableStateFlow(false)

    fun setDragInProgress(inProgress: Boolean) {
        _isDragInProgress.value = inProgress
    }

    /**
     * Sets a bullet ID to scroll to. Called from MainScreen on search/bookmark/tag tap.
     *
     * Also resets the zoom root so the target bullet is guaranteed to be visible in the
     * flat list. Without this, if the user had previously zoomed into a subtree, the target
     * bullet might not appear in flatList (targetIndex == -1) and the scroll would silently fail.
     */
    fun setScrollTarget(bulletId: String) {
        _scrollTarget.value = bulletId
    }

    /** Clears the scroll target after the scroll animation completes. */
    fun clearScrollTarget() {
        _scrollTarget.value = null
    }

    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    private var currentDocumentId: String? = null

    /**
     * Maps real server bullet IDs back to the temp ID that was used as the LazyColumn key.
     * This prevents the full-list re-animation glitch when the server responds and the temp
     * ID is swapped for the real one — LazyColumn sees a stable key throughout.
     */
    private val realIdToTempId = mutableMapOf<String, String>()

    /** Returns a stable key for LazyColumn: the temp ID if this bullet was just created, else its own ID. */
    fun stableKeyFor(bulletId: String): String = realIdToTempId[bulletId] ?: bulletId

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
        realIdToTempId.clear()
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
                    // Load attachments for all bullets in parallel
                    bullets.forEach { bullet ->
                        if (bullet.id !in _attachments.value) {
                            loadAttachments(bullet.id)
                        }
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
    private fun updateState(
        bullets: List<Bullet>,
        focusedBulletId: String? = null,
        focusCursorEnd: Boolean = false,
        forceRebuild: Boolean = false
    ) {
        val rootId = _zoomRootId.value
        val currentState = _uiState.value

        // During a drag, preserve the current flatList to prevent visual snap-back from
        // concurrent state updates (e.g., content-debounce PATCH completing).
        // moveBulletLocally() manages flatList ordering during the drag.
        // forceRebuild overrides this (used by commitBulletMove after server confirms).
        val flatList = if (!forceRebuild && _isDragInProgress.value && currentState is BulletTreeUiState.Success) {
            currentState.flatList
        } else {
            // When hide-completed is on, filter completed bullets before flattening so they
            // and their subtrees disappear from the list without modifying server state.
            val bulletsToDisplay = if (_hideCompleted.value) {
                bullets.filter { !it.isComplete }
            } else {
                bullets
            }
            flattenTreeUseCase(bulletsToDisplay, rootId = rootId)
        }

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
     * Optimistic-insert using the server-returned bullet:
     * - Calls the API and gets the real bullet back with its server-assigned ID.
     * - Inserts the new bullet into the local list immediately after [afterBulletId]
     *   (or at the start of [parentId]'s children if [afterBulletId] is null).
     * - Focuses the new bullet — no second network call needed.
     * On failure: emits snackbar and reloads from server.
     */
    fun createBullet(afterBulletId: String?, parentId: String?) {
        val docId = currentDocumentId ?: return

        // Optimistic insert: show empty bullet immediately (before server round-trip).
        // Compute a position that sorts correctly in FlattenTreeUseCase.
        val bullets = currentBullets()
        val tempId = "temp-${System.nanoTime()}"
        val position = if (afterBulletId != null) {
            val afterBullet = bullets.find { it.id == afterBulletId }
            (afterBullet?.position ?: 0.0) + 0.001
        } else {
            // First child: position before all siblings under parentId
            val firstSibling = bullets.filter { it.parentId == parentId }.minByOrNull { it.position }
            (firstSibling?.position ?: 1.0) - 0.001
        }
        val tempBullet = Bullet(
            id = tempId,
            documentId = docId,
            parentId = parentId,
            content = "",
            position = position,
            isComplete = false,
            isCollapsed = false,
            note = null
        )
        updateState(bullets + tempBullet, focusedBulletId = tempId)

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
                    // Map real ID → temp ID so LazyColumn key stays stable (no re-animation).
                    realIdToTempId[newBullet.id] = tempId

                    // Silently swap temp bullet for real bullet in the backing state
                    // WITHOUT rebuilding the flatList — the UI already shows the bullet
                    // correctly with the temp ID; a full rebuild causes a visible redraw glitch.
                    val currentState = _uiState.value as? BulletTreeUiState.Success ?: return@fold
                    val updatedBullets = currentState.bullets.map { b ->
                        if (b.id == tempId) newBullet else b
                    }
                    val updatedFlatList = currentState.flatList.map { fb ->
                        if (fb.bullet.id == tempId) fb.copy(bullet = newBullet) else fb
                    }
                    // Migrate focus from temp ID to real ID
                    val newFocusId = if (currentState.focusedBulletId == tempId) newBullet.id else currentState.focusedBulletId
                    _uiState.value = currentState.copy(
                        bullets = updatedBullets,
                        flatList = updatedFlatList,
                        focusedBulletId = newFocusId
                    )
                    // Migrate content overrides from temp ID to real ID.
                    val migratedContent = contentOverridesMap.remove(tempId)
                    if (migratedContent != null) {
                        contentOverridesMap[newBullet.id] = migratedContent
                        _contentOverrides.value = contentOverridesMap.toMap()
                        contentEditFlow.tryEmit(newBullet.id to migratedContent)
                    }
                },
                onFailure = {
                    // Remove temp bullet on failure
                    val current = currentBullets()
                    updateState(current.filter { it.id != tempId })
                    _snackbarMessage.emit("Failed to create bullet")
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
     * Optimistic: re-parents locally by finding the previous sibling and updating parentId,
     * then re-flattens immediately for instant UI feedback.
     * On server success: updates the bullet with the authoritative server response (correct position).
     * On failure: emits snackbar and reloads from server to revert.
     */
    fun indentBullet(bulletId: String) {
        // Optimistic update: find previous sibling and reparent locally
        val bullets = currentBullets()
        val flatList = currentFlatList()
        val idx = flatList.indexOfFirst { it.bullet.id == bulletId }
        val bullet = bullets.find { it.id == bulletId }

        if (bullet != null && idx > 0) {
            // Find the previous sibling (same parentId, just before in flat list)
            val prevSibling = (idx - 1 downTo 0)
                .map { flatList[it] }
                .firstOrNull { it.bullet.parentId == bullet.parentId }
            if (prevSibling != null) {
                val optimisticBullets = bullets.map { b ->
                    if (b.id == bulletId) b.copy(parentId = prevSibling.bullet.id) else b
                }
                updateState(optimisticBullets, focusedBulletId = bulletId)
            }
        }

        enqueue {
            indentBulletUseCase(bulletId).fold(
                onSuccess = { updatedBullet ->
                    // Replace with authoritative server bullet (correct position value).
                    val currentBullets = currentBullets()
                    val updatedBullets = currentBullets.map { b ->
                        if (b.id == bulletId) updatedBullet else b
                    }
                    updateState(updatedBullets, focusedBulletId = bulletId)
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
     * Optimistic: re-parents locally by setting parentId to the grandparent,
     * then re-flattens immediately for instant UI feedback.
     * On server success: updates the bullet with the authoritative server response (correct position).
     * On failure: emits snackbar and reloads from server to revert.
     */
    fun outdentBullet(bulletId: String) {
        // Optimistic update: reparent to grandparent
        val bullets = currentBullets()
        val bullet = bullets.find { it.id == bulletId }

        if (bullet?.parentId != null) {
            val parent = bullets.find { it.id == bullet.parentId }
            val grandparentId = parent?.parentId
            val optimisticBullets = bullets.map { b ->
                if (b.id == bulletId) b.copy(parentId = grandparentId) else b
            }
            updateState(optimisticBullets, focusedBulletId = bulletId)
        }

        enqueue {
            outdentBulletUseCase(bulletId).fold(
                onSuccess = { updatedBullet ->
                    // Replace with authoritative server bullet (correct position value).
                    val currentBullets = currentBullets()
                    val updatedBullets = currentBullets.map { b ->
                        if (b.id == bulletId) updatedBullet else b
                    }
                    updateState(updatedBullets, focusedBulletId = bulletId)
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
     * Signals the UI to open a file picker for [bulletId].
     * The UI reads [pendingAttachmentBulletId] and launches the file picker,
     * then calls [clearPendingAttachmentBulletId] and [uploadAttachment].
     */
    fun requestAttachmentUpload(bulletId: String) {
        _pendingAttachmentBulletId.value = bulletId
    }

    /**
     * Clears the pending file picker signal after the UI has launched the picker.
     */
    fun clearPendingAttachmentBulletId() {
        _pendingAttachmentBulletId.value = null
    }

    /**
     * Uploads a file (from a content URI obtained via file picker) as an attachment
     * on [bulletId]. On success, reloads the attachment list for that bullet and
     * expands the attachment section so the user sees the new file.
     */
    fun uploadAttachment(bulletId: String, uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            // Resolve filename and MIME type from the content URI
            var filename = "attachment"
            var mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    filename = cursor.getString(nameIndex)
                }
            }
            uploadAttachmentUseCase(bulletId, uri, filename, mimeType).fold(
                onSuccess = { newAttachment ->
                    // Append to cached attachment list for this bullet
                    val existing = _attachments.value[bulletId] ?: emptyList()
                    _attachments.value = _attachments.value + (bulletId to existing + newAttachment)
                    // Ensure the attachment section is expanded so user sees new file
                    _expandedAttachments.value = _expandedAttachments.value + bulletId
                    _snackbarMessage.emit("File uploaded successfully")
                },
                onFailure = {
                    _snackbarMessage.emit("Upload failed: ${it.message}")
                }
            )
        }
    }

    /**
     * Deletes an attachment by ID. Optimistically removes it from the cached list for
     * the attachment's bullet. Emits a snackbar on failure and reloads the attachment list.
     */
    fun deleteAttachment(attachment: Attachment) {
        val bulletId = attachment.bulletId
        // Optimistic removal
        val existing = _attachments.value[bulletId] ?: emptyList()
        _attachments.value = _attachments.value + (bulletId to existing.filter { it.id != attachment.id })

        viewModelScope.launch {
            deleteAttachmentUseCase(attachment.id).fold(
                onSuccess = {
                    // Attachment removed — local state already updated optimistically
                },
                onFailure = {
                    // Revert: reload attachment list for this bullet
                    _snackbarMessage.emit("Failed to remove attachment: ${it.message}")
                    getAttachmentsUseCase(bulletId).onSuccess { attachments ->
                        _attachments.value = _attachments.value + (bulletId to attachments)
                    }
                }
            )
        }
    }

    /**
     * Refreshes bullets from server, setting [_isRefreshing] appropriately.
     * Used by pull-to-refresh (Plan 04).
     *
     * Flushes any pending local edits first so the server has the latest content,
     * then clears overrides so the UI shows the freshly-fetched server state.
     */
    fun refresh() {
        val docId = currentDocumentId ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Flush pending edits so the server has our latest content
                val pendingContent = contentOverridesMap.toMap()
                val pendingNotes = noteOverrides.toMap()
                pendingContent.forEach { (bulletId, content) ->
                    patchBulletUseCase(bulletId, PatchBulletRequest.updateContent(content))
                }
                pendingNotes.forEach { (bulletId, note) ->
                    patchBulletUseCase(bulletId, PatchBulletRequest.updateNote(note))
                }
                // Clear overrides so the UI shows server-authoritative content
                contentOverridesMap.clear()
                _contentOverrides.value = emptyMap()
                noteOverrides.clear()

                getBulletsUseCase(docId).onSuccess { bullets ->
                    updateState(bullets, forceRebuild = true)
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
     * Toggles visibility of completed bullets in the flat list.
     * Does not delete them — just filters the display. Re-flattens current bullet list.
     */
    fun toggleHideCompleted() {
        _hideCompleted.value = !_hideCompleted.value
        updateState(currentBullets())
    }

    /**
     * Deletes all completed bullets in the current document from the server.
     * Optimistic: removes them from local state immediately.
     */
    fun deleteAllCompleted() {
        val bullets = currentBullets()
        val completedIds = bullets.filter { it.isComplete }.map { it.id }
        if (completedIds.isEmpty()) return

        val optimisticBullets = bullets.filter { !it.isComplete }
        updateState(optimisticBullets)

        enqueue {
            var anyFailure = false
            for (id in completedIds) {
                deleteBulletUseCase(id).onFailure { anyFailure = true }
            }
            if (anyFailure) {
                _snackbarMessage.emit("Some completed bullets could not be deleted")
                reloadFromServer()
            }
        }
    }

    /**
     * Immediately PATCHes the content for [bulletId] without waiting for the debounce.
     * Called when a bullet loses focus so that in-progress edits are persisted even if the
     * user navigates away or the OS kills the app before the 500ms debounce fires.
     */
    fun flushContentEdit(bulletId: String) {
        val content = contentOverridesMap[bulletId] ?: return
        viewModelScope.launch {
            patchBulletUseCase(bulletId, PatchBulletRequest.updateContent(content))
                .onSuccess {
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
     *
     * IMPORTANT: [currentBullets()] is called INSIDE the enqueue lambda so it reads the
     * live state AFTER [moveBulletLocally] has already reordered the flatList during dragging.
     * Capturing bullets before enqueue{} would read stale pre-drag state and cause snap-back.
     */
    fun commitBulletMove(bulletId: String, newParentId: String?, afterId: String?) {
        enqueue {
            moveBulletUseCase(
                bulletId,
                MoveBulletRequest(newParentId = newParentId, afterId = afterId)
            ).fold(
                onSuccess = {
                    // Clear drag flag and reload full tree from server to ensure all
                    // bullets (including siblings whose positions may have shifted)
                    // are correct. forceRebuild ensures the flatList is regenerated.
                    _isDragInProgress.value = false
                    val docId = currentDocumentId ?: return@fold
                    getBulletsUseCase(docId).onSuccess { bullets ->
                        updateState(bullets, forceRebuild = true)
                    }
                },
                onFailure = {
                    _isDragInProgress.value = false
                    _snackbarMessage.emit("Failed to move bullet")
                    reloadFromServer()
                }
            )
        }
    }

    /**
     * Synchronously PATCHes all pending content and note overrides to the server.
     * Called from onCleared() and from the lifecycle observer when the app is backgrounded.
     */
    fun flushAllPendingEdits() {
        val pendingContent = contentOverridesMap.toMap()
        val pendingNotes = noteOverrides.toMap()
        if (pendingContent.isEmpty() && pendingNotes.isEmpty()) return
        runBlocking {
            pendingContent.forEach { (bulletId, content) ->
                patchBulletUseCase(bulletId, PatchBulletRequest.updateContent(content))
            }
            pendingNotes.forEach { (bulletId, note) ->
                patchBulletUseCase(bulletId, PatchBulletRequest.updateNote(note))
            }
        }
        contentOverridesMap.clear()
        _contentOverrides.value = emptyMap()
        noteOverrides.clear()
    }

    override fun onCleared() {
        flushAllPendingEdits()
        super.onCleared()
    }
}
