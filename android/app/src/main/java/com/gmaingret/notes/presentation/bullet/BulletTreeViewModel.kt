package com.gmaingret.notes.presentation.bullet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.usecase.CreateBulletUseCase
import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import com.gmaingret.notes.domain.usecase.FlattenTreeUseCase
import com.gmaingret.notes.domain.usecase.GetBulletsUseCase
import com.gmaingret.notes.domain.usecase.GetUndoStatusUseCase
import com.gmaingret.notes.domain.usecase.IndentBulletUseCase
import com.gmaingret.notes.domain.usecase.MoveBulletUseCase
import com.gmaingret.notes.domain.usecase.OutdentBulletUseCase
import com.gmaingret.notes.domain.usecase.PatchBulletUseCase
import com.gmaingret.notes.domain.usecase.RedoUseCase
import com.gmaingret.notes.domain.usecase.UndoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the bullet tree editor screen.
 *
 * Responsibilities:
 * - Load bullets for a document and maintain [uiState]
 * - Serialize all structural operations through [operationQueue] to prevent race conditions
 * - Expose undo/redo cursor state for toolbar button enabled states
 * - Support zoom-into-bullet mode with breadcrumb navigation
 *
 * Operations stubbed here are fully implemented in Plan 02 (BulletTreeViewModel operations + tests).
 */
@HiltViewModel
class BulletTreeViewModel @Inject constructor(
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
    private val flattenTreeUseCase: FlattenTreeUseCase
) : ViewModel() {

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

    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    private var currentDocumentId: String? = null

    // -----------------------------------------------------------------------
    // Operation queue — serializes all server calls to prevent race conditions
    // -----------------------------------------------------------------------

    private val operationQueue = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (op in operationQueue) { op() }
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
     * Also fetches initial undo status to set toolbar button states.
     */
    fun loadBullets(documentId: String) {
        currentDocumentId = documentId
        _uiState.value = BulletTreeUiState.Loading
        viewModelScope.launch {
            getBulletsUseCase(documentId).fold(
                onSuccess = { bullets ->
                    updateState(bullets)
                    loadUndoStatus()
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
     */
    private fun updateState(bullets: List<Bullet>) {
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
        val focusedId = (currentState as? BulletTreeUiState.Success)?.focusedBulletId
        _uiState.value = BulletTreeUiState.Success(
            bullets = bullets,
            flatList = flatList,
            focusedBulletId = focusedId
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

    // -----------------------------------------------------------------------
    // Stubbed operation methods — implemented in Plan 02
    // -----------------------------------------------------------------------

    fun createBullet(afterBulletId: String?, parentId: String?) {
        // TODO: Plan 02 — enqueue createBulletUseCase call with optimistic insert
    }

    fun deleteBullet(bulletId: String) {
        // TODO: Plan 02 — enqueue deleteBulletUseCase call with optimistic removal
    }

    fun indentBullet(bulletId: String) {
        // TODO: Plan 02 — enqueue indentBulletUseCase call with optimistic re-parent
    }

    fun outdentBullet(bulletId: String) {
        // TODO: Plan 02 — enqueue outdentBulletUseCase call with optimistic re-parent
    }

    fun moveUp(bulletId: String) {
        // TODO: Plan 02 — enqueue moveBullet call to swap with previous sibling (cross-parent)
    }

    fun moveDown(bulletId: String) {
        // TODO: Plan 02 — enqueue moveBullet call to swap with next sibling (cross-parent)
    }

    fun toggleCollapse(bulletId: String) {
        // TODO: Plan 02 — enqueue patchBulletUseCase with isCollapsed toggle
    }

    fun updateContent(bulletId: String, content: String) {
        // TODO: Plan 02 — debounced 500ms patchBulletUseCase with content
    }

    fun saveNote(bulletId: String, note: String) {
        // TODO: Plan 02 — debounced 500ms patchBulletUseCase with note
    }

    fun undo() {
        // TODO: Plan 02 — enqueue undoUseCase, then reloadFromServer + loadUndoStatus
    }

    fun redo() {
        // TODO: Plan 02 — enqueue redoUseCase, then reloadFromServer + loadUndoStatus
    }

    fun setFocusedBullet(bulletId: String?) {
        // TODO: Plan 02 — update focusedBulletId in Success state
    }

    fun zoomTo(bulletId: String?) {
        // TODO: Plan 02 — set _zoomRootId, call updateState with current bullets
    }

    fun enterOnEmpty(bulletId: String) {
        // TODO: Plan 02 — if root level: no-op (or unfocus); else outdent
    }

    fun backspaceOnEmpty(bulletId: String) {
        // TODO: Plan 02 — delete bullet, children reparent to previous sibling, focus previous
    }

    fun moveBulletLocally(fromIndex: Int, toIndex: Int) {
        // TODO: Plan 02 — optimistic reorder in flatList for drag preview
    }

    fun commitBulletMove(bulletId: String, newParentId: String?, afterId: String?) {
        // TODO: Plan 02 — enqueue moveBulletUseCase after drag ends, revert on failure
    }
}
