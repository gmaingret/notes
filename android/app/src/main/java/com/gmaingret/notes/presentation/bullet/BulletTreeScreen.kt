package com.gmaingret.notes.presentation.bullet

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

/**
 * Top-level composable for the bullet tree editor.
 *
 * Collects [BulletTreeUiState] from the [BulletTreeViewModel] and renders:
 * - [BulletTreeUiState.Loading]: shimmer skeleton rows
 * - [BulletTreeUiState.Success]: LazyColumn of [BulletRow]s with toolbar and drag-reorder
 * - [BulletTreeUiState.Error]: error message with retry button
 *
 * Features:
 * - Drag-reorder with long-press: uses Calvin-LL/Reorderable pattern (same as Phase 10 DocumentDrawerContent)
 * - Reparenting via horizontal drag: horizontal displacement snapped to INDENT_DP units determines
 *   target depth delta; newParentId computed by walking up from drop position
 * - Cycle prevention: moving a bullet under its own descendant is blocked with a Snackbar
 * - BreadcrumbRow: AnimatedVisibility strip shown when zoomed into a subtree
 * - Note expansion: Set<String> tracks which bullet IDs have their note field open;
 *   toolbar comment button toggles the focused bullet's note
 * - Collapse/expand animations handled by LazyColumn animateItem() modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulletTreeScreen(
    documentId: String,
    documentTitle: String,
    modifier: Modifier = Modifier,
    pendingScrollToBulletId: String? = null,
    onClearPendingScroll: () -> Unit = {},
    onChipClick: ((String) -> Unit)? = null,
    viewModel: BulletTreeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val zoomRootId by viewModel.zoomRootId.collectAsState()
    val breadcrumbPath by viewModel.breadcrumbPath.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val contentOverrides by viewModel.contentOverrides.collectAsState()
    val bookmarkedBulletIds by viewModel.bookmarkedBulletIds.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val expandedAttachments by viewModel.expandedAttachments.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val hideCompleted by viewModel.hideCompleted.collectAsState()
    val pendingAttachmentBulletId by viewModel.pendingAttachmentBulletId.collectAsState()
    val vmScrollTarget by viewModel.scrollTarget.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Date picker state — triggered when user types "!!" in a bullet
    var datePickerBulletId by remember { mutableStateOf<String?>(null) }

    // Stable ref to the active upload bullet ID so the file picker callback sees the latest value
    var uploadTargetBulletId by remember { mutableStateOf<String?>(null) }

    // File picker launcher — opens the system file picker and uploads the chosen file
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val bulletId = uploadTargetBulletId
        uploadTargetBulletId = null
        viewModel.clearPendingAttachmentBulletId()
        if (uri != null && bulletId != null) {
            viewModel.uploadAttachment(bulletId, uri)
        }
    }

    // Launch file picker when ViewModel signals a pending attachment upload
    LaunchedEffect(pendingAttachmentBulletId) {
        val bulletId = pendingAttachmentBulletId
        if (bulletId != null) {
            uploadTargetBulletId = bulletId
            filePickerLauncher.launch("*/*")
        }
    }

    // Flush pending edits on background, reload bullets on foreground resume.
    // ON_START fires when the app returns from the background (e.g. after using
    // the widget's add-bullet overlay), ensuring externally-created bullets appear.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        var hasStoppedOnce = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    viewModel.flushAllPendingEdits()
                    hasStoppedOnce = true
                }
                Lifecycle.Event.ON_START -> {
                    // Only reload after the screen has been backgrounded at least once
                    // to avoid double-loading on initial composition.
                    // Use silentReload() instead of loadBullets() so we don't blank the
                    // screen or show an error if the network is temporarily unavailable.
                    if (hasStoppedOnce) {
                        viewModel.silentReload()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Consume snackbar messages from ViewModel
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Load bullets when the document changes
    LaunchedEffect(documentId) {
        viewModel.loadBullets(documentId)
    }

    // Track which bullets have their note field expanded (by bullet ID)
    var expandedNoteIds by remember { mutableStateOf(setOf<String>()) }

    // LazyColumn state hoisted outside the when-block so scroll position survives state changes
    val lazyListState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Breadcrumb row — animated, only visible when zoomed into a bullet
            AnimatedVisibility(visible = breadcrumbPath.isNotEmpty()) {
                BreadcrumbRow(
                    breadcrumbs = breadcrumbPath,
                    onCrumbClick = { bulletId -> viewModel.zoomTo(bulletId) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is BulletTreeUiState.Loading -> {
                        ShimmerBulletRows()
                    }

                    is BulletTreeUiState.Success -> {
                        // Auto-create empty bullet if document has no bullets
                        LaunchedEffect(state.flatList.isEmpty()) {
                            if (state.flatList.isEmpty()) {
                                viewModel.createBullet(null, zoomRootId)
                            }
                        }

                        val focusedBulletId = state.focusedBulletId
                        val flatList = state.flatList

                        // Scroll to focused bullet when it changes.
                        // Three cases:
                        // 1. Already fully visible → do nothing
                        // 2. Above viewport → scroll to show at top
                        // 3. Below or partially clipped at bottom (Enter key case) →
                        //    scroll to show previous bullet at top, new bullet below it
                        LaunchedEffect(focusedBulletId) {
                            if (focusedBulletId != null) {
                                val idx = flatList.indexOfFirst { it.bullet.id == focusedBulletId }
                                if (idx >= 0) {
                                    val layoutInfo = lazyListState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    val itemInfo = visibleItems.find { it.index == idx }

                                    val fullyVisible = itemInfo != null &&
                                        itemInfo.offset >= 0 &&
                                        (itemInfo.offset + itemInfo.size) <= layoutInfo.viewportEndOffset

                                    if (!fullyVisible) {
                                        val isAbove = itemInfo == null &&
                                            visibleItems.isNotEmpty() &&
                                            idx < visibleItems.first().index
                                        if (isAbove) {
                                            // Item is above viewport — scroll to show at top
                                            lazyListState.scrollToItem(idx)
                                        } else if (idx > 0) {
                                            // Item is below viewport OR partially clipped at bottom.
                                            // Scroll so previous bullet is at top — new bullet
                                            // will be visible right below it.
                                            lazyListState.scrollToItem(idx - 1)
                                        } else {
                                            lazyListState.scrollToItem(0)
                                        }
                                    }
                                }
                            }
                        }

                        // When IME (keyboard) opens, scroll to keep focused bullet visible.
                        // With adjustNothing the LazyColumn doesn't resize, so we
                        // must manually ensure the focused item isn't behind the keyboard.
                        val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                        LaunchedEffect(imeBottom) {
                            if (imeBottom > 0 && focusedBulletId != null) {
                                val idx = flatList.indexOfFirst { it.bullet.id == focusedBulletId }
                                if (idx >= 0) {
                                    val layoutInfo = lazyListState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    val itemInfo = visibleItems.find { it.index == idx }
                                    // Check if item is behind the keyboard
                                    val visibleEnd = layoutInfo.viewportEndOffset - imeBottom
                                    val clipped = itemInfo == null ||
                                        (itemInfo.offset + itemInfo.size) > visibleEnd
                                    if (clipped && idx > 0) {
                                        lazyListState.animateScrollToItem(idx - 1)
                                    } else if (clipped) {
                                        lazyListState.animateScrollToItem(0)
                                    }
                                }
                            }
                        }

                        // Compute toolbar enabled states
                        val focusedIndex = flatList.indexOfFirst { it.bullet.id == focusedBulletId }
                        val focusedBullet = if (focusedIndex >= 0) flatList[focusedIndex] else null
                        val canIndent = focusedBullet?.let { fb ->
                            // Can indent if there is a previous sibling (same parentId) anywhere above in the flat list.
                            // Must scan backwards because after outdent the immediately previous item may be
                            // at a different depth (a child of a different parent).
                            if (focusedIndex <= 0) false
                            else {
                                var found = false
                                for (i in (focusedIndex - 1) downTo 0) {
                                    val candidate = flatList[i]
                                    if (candidate.bullet.parentId == fb.bullet.parentId) {
                                        found = true
                                        break
                                    }
                                    // Stop scanning if we've gone past possible siblings
                                    // (reached a bullet at a shallower depth than the focused bullet)
                                    if (candidate.depth < fb.depth) break
                                }
                                found
                            }
                        } ?: false
                        val canOutdent = focusedBullet?.bullet?.parentId != null
                        val canMoveUp = focusedIndex > 0
                        val canMoveDown = focusedIndex >= 0 && focusedIndex < flatList.size - 1

                        // Drag state for reparenting
                        val view = LocalView.current
                        val density = LocalDensity.current
                        val keyboardController = LocalSoftwareKeyboardController.current
                        val focusManager = LocalFocusManager.current
                        var dragHorizontalOffset by remember { mutableFloatStateOf(0f) }
                        var draggedBulletId by remember { mutableStateOf<String?>(null) }
                        // Index of the dragged item at the START of the drag (to detect no-move long-press)
                        var dragStartIndex by remember { mutableStateOf(-1) }
                        // Bullet ID for which the context menu should show (after a no-drag long-press)
                        var contextMenuBulletId by remember { mutableStateOf<String?>(null) }

                        // Scroll to bullet when either pendingScrollToBulletId (from MainViewModel
                        // through Crossfade parameter) or vmScrollTarget (set directly on VM by
                        // MainScreen search/bookmark handlers) changes.
                        // vmScrollTarget is the primary path — it bypasses Crossfade parameter
                        // passing which was found unreliable across 3 rounds of user feedback.
                        // Re-check on flatList size changes: when navigating to a different document
                        // the flatList may be empty when the ID first arrives. The LaunchedEffect
                        // re-fires once the flatList grows and we find the target bullet.
                        val effectiveScrollTarget = pendingScrollToBulletId ?: vmScrollTarget
                        LaunchedEffect(effectiveScrollTarget) {
                            if (effectiveScrollTarget != null) {
                                // Zoom into the target bullet — shows it in the breadcrumb
                                // with its children below.
                                viewModel.zoomTo(effectiveScrollTarget)
                                viewModel.clearScrollTarget()
                                onClearPendingScroll()
                                lazyListState.scrollToItem(0)
                            }
                        }

                        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                            viewModel.moveBulletLocally(from.index, to.index)
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.refresh() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                contentPadding = WindowInsets.ime.asPaddingValues()
                            ) {
                                items(
                                    items = flatList,
                                    key = { viewModel.stableKeyFor(it.bullet.id) }
                                ) { flatBullet ->
                                    ReorderableItem(reorderableState, key = viewModel.stableKeyFor(flatBullet.bullet.id)) { isDragging ->
                                        val indentPerLevel = 28.dp
                                        val targetDepthDelta = if (isDragging) {
                                            val indentPx = with(density) { indentPerLevel.toPx() }
                                            // Max depth: one level deeper than the bullet directly above.
                                            // This replaces the old hardcoded +1 clamp that prevented
                                            // dragging a root node directly to a deep nesting level.
                                            val liveFl = (viewModel.uiState.value as? BulletTreeUiState.Success)?.flatList ?: flatList
                                            val myIdx = liveFl.indexOfFirst { it.bullet.id == flatBullet.bullet.id }
                                            val aboveDepth = if (myIdx > 0) liveFl[myIdx - 1].depth else -1
                                            val maxDelta = (aboveDepth + 1) - flatBullet.depth
                                            (dragHorizontalOffset / indentPx).roundToInt()
                                                .coerceIn(-flatBullet.depth, maxDelta.coerceAtLeast(0))
                                        } else 0

                                        val isFocusedBullet = flatBullet.bullet.id == focusedBulletId
                                        var hapticFired by remember { mutableStateOf(false) }

                                        // Custom swipe: track horizontal offset with full control
                                        val swipeOffset = remember { Animatable(0f) }
                                        val swipeScope = rememberCoroutineScope()
                                        val rowWidthPx = with(density) { 360.dp.toPx() } // fallback; overridden by onSizeChanged

                                        Box {
                                            // Background layer — only visible while actively dragging
                                            if (swipeOffset.value != 0f) {
                                                val maxWidthPx = with(density) { 400.dp.toPx() } // approximate
                                                val progress = (kotlin.math.abs(swipeOffset.value) / (maxWidthPx * 0.25f)).coerceIn(0f, 1f)
                                                val isRight = swipeOffset.value > 0f
                                                val bgColor = if (isRight) Color(0xFF22C55E) else Color(0xFFEF4444)
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(bgColor.copy(alpha = progress)),
                                                    contentAlignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd
                                                ) {
                                                    val iconTint = Color.White.copy(alpha = progress)
                                                    if (isRight) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = "Complete",
                                                            tint = iconTint,
                                                            modifier = Modifier.padding(start = 16.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Filled.Delete,
                                                            contentDescription = "Delete",
                                                            tint = iconTint,
                                                            modifier = Modifier.padding(end = 16.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Foreground row — slides with finger
                                            Box(
                                                modifier = Modifier
                                                    .offset { androidx.compose.ui.unit.IntOffset(swipeOffset.value.roundToInt(), 0) }
                                                    .pointerInput(isDragging) {
                                                        if (isDragging) return@pointerInput
                                                        detectHorizontalDragGestures(
                                                            onDragEnd = {
                                                                val widthPx = size.width.toFloat()
                                                                val threshold = widthPx * 0.25f
                                                                val offset = swipeOffset.value
                                                                if (offset > threshold) {
                                                                    // Swipe right past 50% — complete
                                                                    viewModel.toggleComplete(flatBullet.bullet.id)
                                                                } else if (offset < -threshold) {
                                                                    // Swipe left past 50% — delete
                                                                    viewModel.deleteBullet(flatBullet.bullet.id)
                                                                }
                                                                // Animate back to 0
                                                                swipeScope.launch {
                                                                    swipeOffset.animateTo(0f, tween(200))
                                                                }
                                                                hapticFired = false
                                                            },
                                                            onDragCancel = {
                                                                swipeScope.launch {
                                                                    swipeOffset.animateTo(0f, tween(200))
                                                                }
                                                                hapticFired = false
                                                            },
                                                            onHorizontalDrag = { _, dragAmount ->
                                                                swipeScope.launch {
                                                                    swipeOffset.snapTo(swipeOffset.value + dragAmount)
                                                                }
                                                                // Haptic at 50% threshold
                                                                val widthPx = size.width.toFloat()
                                                                val threshold = widthPx * 0.25f
                                                                val pastThreshold = kotlin.math.abs(swipeOffset.value) >= threshold
                                                                if (pastThreshold && !hapticFired) {
                                                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                                    hapticFired = true
                                                                } else if (!pastThreshold) {
                                                                    hapticFired = false
                                                                }
                                                            }
                                                        )
                                                    }
                                            ) {
                                        BulletRow(
                                            flatBullet = flatBullet,
                                            isFocused = flatBullet.bullet.id == focusedBulletId,
                                            contentOverride = contentOverrides[flatBullet.bullet.id],
                                            focusCursorEnd = state.focusCursorEnd,
                                            isDragging = isDragging,
                                            dragHorizontalOffsetPx = if (isDragging) dragHorizontalOffset else 0f,
                                            isNoteExpanded = flatBullet.bullet.id in expandedNoteIds,
                                            isBookmarked = flatBullet.bullet.id in bookmarkedBulletIds,
                                            isAttachmentsExpanded = flatBullet.bullet.id in expandedAttachments,
                                            attachments = attachments[flatBullet.bullet.id] ?: emptyList(),
                                            onFocusRequest = {
                                                viewModel.setFocusedBullet(flatBullet.bullet.id)
                                            },
                                            onFocusLost = {
                                                viewModel.flushContentEdit(flatBullet.bullet.id)
                                            },
                                            onContentChange = { content ->
                                                viewModel.updateContent(flatBullet.bullet.id, content)
                                                // Trigger date picker when user just typed "!!" (content ends with it)
                                                if (datePickerBulletId == null && content.endsWith("!!")) {
                                                    datePickerBulletId = flatBullet.bullet.id
                                                }
                                            },
                                            onEnterWithContent = {
                                                // If bullet has visible children (has children and not collapsed),
                                                // Enter creates a new first child instead of a sibling.
                                                if (flatBullet.hasChildren && !flatBullet.bullet.isCollapsed) {
                                                    viewModel.createBullet(
                                                        afterBulletId = null,
                                                        parentId = flatBullet.bullet.id
                                                    )
                                                } else {
                                                    viewModel.createBullet(
                                                        afterBulletId = flatBullet.bullet.id,
                                                        parentId = flatBullet.bullet.parentId
                                                    )
                                                }
                                            },
                                            onEnterOnEmpty = {
                                                viewModel.enterOnEmpty(flatBullet.bullet.id)
                                            },
                                            onBackspaceOnEmpty = {
                                                viewModel.backspaceOnEmpty(flatBullet.bullet.id)
                                            },
                                            onCollapseToggle = {
                                                viewModel.toggleCollapse(flatBullet.bullet.id)
                                            },
                                            onBulletIconTap = {
                                                viewModel.zoomTo(flatBullet.bullet.id)
                                            },
                                            onToggleNote = {
                                                val bulletId = flatBullet.bullet.id
                                                expandedNoteIds = if (bulletId in expandedNoteIds) {
                                                    expandedNoteIds - bulletId
                                                } else {
                                                    expandedNoteIds + bulletId
                                                }
                                            },
                                            onNoteChange = { note ->
                                                viewModel.saveNote(flatBullet.bullet.id, note)
                                            },
                                            onToggleComplete = {
                                                viewModel.toggleComplete(flatBullet.bullet.id)
                                            },
                                            onToggleBookmark = {
                                                viewModel.toggleBookmark(flatBullet.bullet.id)
                                            },
                                            onToggleAttachments = {
                                                viewModel.toggleAttachmentExpansion(flatBullet.bullet.id)
                                            },
                                            onDeleteFromMenu = {
                                                viewModel.deleteBullet(flatBullet.bullet.id)
                                            },
                                            onDownloadAttachment = { attachment ->
                                                viewModel.downloadAttachment(attachment)
                                            },
                                            onDeleteAttachment = { attachment ->
                                                viewModel.deleteAttachment(attachment)
                                            },
                                            onChipClick = if (flatBullet.bullet.id != focusedBulletId) onChipClick else null,
                                            showContextMenuExternal = contextMenuBulletId == flatBullet.bullet.id,
                                            onContextMenuDismiss = { contextMenuBulletId = null },
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                        draggedBulletId = flatBullet.bullet.id
                                                        dragHorizontalOffset = 0f
                                                        viewModel.setDragInProgress(true)
                                                        // Capture the index at drag start to detect no-move long-press
                                                        val currentFlatList = (viewModel.uiState.value as? BulletTreeUiState.Success)?.flatList ?: flatList
                                                        dragStartIndex = currentFlatList.indexOfFirst { it.bullet.id == flatBullet.bullet.id }
                                                    },
                                                    onDragStopped = {
                                                        val bullet = flatBullet.bullet
                                                        // Read the CURRENT flatList from ViewModel state at drop time.
                                                        // The captured `flatList` is stale after moveBulletLocally() has
                                                        // already reordered it — using it would compute afterId/newParentId
                                                        // from the original positions, causing snap-back on release.
                                                        val liveFlatList = (viewModel.uiState.value as? BulletTreeUiState.Success)?.flatList
                                                            ?: flatList
                                                        val currentIndex = liveFlatList.indexOfFirst { it.bullet.id == bullet.id }

                                                        // Detect no-drag long-press: same position AND negligible horizontal offset.
                                                        // In this case show context menu instead of committing a move.
                                                        val indentPx = with(density) { 28.dp.toPx() }
                                                        val horizontalDeltaSteps = (dragHorizontalOffset / indentPx).roundToInt()
                                                        val didNotMove = currentIndex == dragStartIndex && horizontalDeltaSteps == 0
                                                        if (didNotMove) {
                                                            // Long-press without drag — show context menu after finger lift
                                                            contextMenuBulletId = bullet.id
                                                            draggedBulletId = null
                                                            dragHorizontalOffset = 0f
                                                            dragStartIndex = -1
                                                            viewModel.setDragInProgress(false)
                                                        } else {
                                                            // IMPORTANT: targetDepthDelta is a local val computed from isDragging,
                                                            // which is already false when onDragStopped fires. Recompute here
                                                            // directly from dragHorizontalOffset (which IS state and holds the
                                                            // accumulated horizontal displacement at drop time).
                                                            val currentDepth = if (currentIndex >= 0) liveFlatList[currentIndex].depth else flatBullet.depth
                                                            // Max depth: one level deeper than the bullet above the drop position
                                                            val aboveDropDepth = if (currentIndex > 0) liveFlatList[currentIndex - 1].depth else -1
                                                            val maxDropDelta = (aboveDropDepth + 1) - currentDepth
                                                            val droppedDepthDelta = (dragHorizontalOffset / indentPx).roundToInt()
                                                                .coerceIn(-currentDepth, maxDropDelta.coerceAtLeast(0))
                                                            val targetDepth = (currentDepth + droppedDepthDelta).coerceAtLeast(0)

                                                            // Find newParentId: walk up from drop position to find bullet at targetDepth-1.
                                                            // Stop if we hit a bullet shallower than targetDepth-1 — that means
                                                            // the target depth is too deep for this position. Clamp to depth of
                                                            // the bullet just above + 1 (making the dropped item its child).
                                                            var newParentId: String? = null
                                                            var effectiveTargetDepth = targetDepth
                                                            if (effectiveTargetDepth > 0 && currentIndex > 0) {
                                                                var found = false
                                                                for (i in (currentIndex - 1) downTo 0) {
                                                                    if (liveFlatList[i].depth == effectiveTargetDepth - 1) {
                                                                        newParentId = liveFlatList[i].bullet.id
                                                                        found = true
                                                                        break
                                                                    }
                                                                    if (liveFlatList[i].depth < effectiveTargetDepth - 1) {
                                                                        // Target depth too deep — clamp to child of this bullet
                                                                        effectiveTargetDepth = liveFlatList[i].depth + 1
                                                                        newParentId = liveFlatList[i].bullet.id
                                                                        found = true
                                                                        break
                                                                    }
                                                                }
                                                                if (!found) newParentId = null
                                                            }

                                                            // Cycle prevention: newParentId must NOT be a descendant of the dragged bullet
                                                            val isDescendant = if (newParentId != null) {
                                                                // Descendants are bullets between dragged index and the next at same-or-lower depth
                                                                val descendantIds = mutableSetOf<String>()
                                                                if (currentIndex >= 0) {
                                                                    val draggedDepth = currentDepth
                                                                    for (i in (currentIndex + 1) until liveFlatList.size) {
                                                                        if (liveFlatList[i].depth > draggedDepth) {
                                                                            descendantIds.add(liveFlatList[i].bullet.id)
                                                                        } else {
                                                                            break
                                                                        }
                                                                    }
                                                                }
                                                                newParentId in descendantIds
                                                            } else false

                                                            if (isDescendant) {
                                                                viewModel.showSnackbar("Cannot move bullet under its own child")
                                                            } else {
                                                                // Find afterId: previous sibling at same depth under same parent
                                                                var afterId: String? = null
                                                                if (currentIndex > 0) {
                                                                    for (i in (currentIndex - 1) downTo 0) {
                                                                        val candidate = liveFlatList[i]
                                                                        if (candidate.bullet.parentId == newParentId && candidate.depth == effectiveTargetDepth) {
                                                                            afterId = candidate.bullet.id
                                                                            break
                                                                        }
                                                                        if (candidate.depth < effectiveTargetDepth) break
                                                                    }
                                                                }

                                                                viewModel.commitBulletMove(
                                                                    bulletId = bullet.id,
                                                                    newParentId = newParentId,
                                                                    afterId = afterId
                                                                )
                                                            }
                                                        }

                                                        draggedBulletId = null
                                                        dragHorizontalOffset = 0f
                                                        dragStartIndex = -1
                                                    },
                                                    // Use a custom DragGestureDetector wrapping LongPress so that
                                                    // we receive each drag delta to accumulate horizontal offset.
                                                    // The plain longPressDraggableHandle modifier consumes all
                                                    // pointer events, making a separate pointerInput for horizontal
                                                    // tracking impossible. Wrapping via DragGestureDetector lets us
                                                    // intercept onDrag(change, dragAmount) before the library handles it.
                                                    dragGestureDetector = object : DragGestureDetector {
                                                        override suspend fun PointerInputScope.detect(
                                                            onDragStart: (androidx.compose.ui.geometry.Offset) -> Unit,
                                                            onDragEnd: () -> Unit,
                                                            onDragCancel: () -> Unit,
                                                            onDrag: (PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit
                                                        ) {
                                                            with(DragGestureDetector.LongPress) {
                                                                detect(
                                                                    onDragStart = onDragStart,
                                                                    onDragEnd = onDragEnd,
                                                                    onDragCancel = onDragCancel,
                                                                    onDrag = { change, dragAmount ->
                                                                        // Accumulate horizontal offset before forwarding
                                                                        dragHorizontalOffset += dragAmount.x
                                                                        onDrag(change, dragAmount)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                )
                                        )
                                        } // end foreground Box
                                        } // end custom swipe Box
                                    }
                                }
                                // Tappable empty zone below bullets — clears focus and hides keyboard
                                item(key = "__empty_zone__") {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(800.dp)
                                            .pointerInput(Unit) {
                                                detectTapGestures {
                                                    viewModel.setFocusedBullet(null)
                                                    keyboardController?.hide()
                                                    focusManager.clearFocus()
                                                }
                                            }
                                    )
                                }
                            }
                            } // end PullToRefreshBox

                            // Completed bullets toolbar — shown when any bullet is completed
                            val hasCompleted = state.bullets.any { it.isComplete }
                            AnimatedVisibility(visible = hasCompleted && focusedBulletId == null) {
                                Column {
                                    HorizontalDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { viewModel.toggleHideCompleted() }) {
                                            Text(if (hideCompleted) "Show completed" else "Hide completed")
                                        }
                                        TextButton(onClick = { viewModel.deleteAllCompleted() }) {
                                            Text(
                                                "Delete completed",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            // Editing toolbar — animated, shown only when a bullet is focused
                            AnimatedVisibility(
                                visible = focusedBulletId != null,
                                modifier = Modifier.imePadding(),
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it }
                            ) {
                                focusedBulletId?.let { bulletId ->
                                    BulletEditingToolbar(
                                        bulletId = bulletId,
                                        canIndent = canIndent,
                                        canOutdent = canOutdent,
                                        canMoveUp = canMoveUp,
                                        canMoveDown = canMoveDown,
                                        canUndo = canUndo,
                                        canRedo = canRedo,
                                        hasNote = focusedBullet?.bullet?.note?.isNotEmpty() == true,
                                        onIndent = { viewModel.indentBullet(bulletId) },
                                        onOutdent = { viewModel.outdentBullet(bulletId) },
                                        onMoveUp = { viewModel.moveUp(bulletId) },
                                        onMoveDown = { viewModel.moveDown(bulletId) },
                                        onUndo = { viewModel.undo() },
                                        onRedo = { viewModel.redo() },
                                        onComment = {
                                            // Toggle note expansion for focused bullet
                                            expandedNoteIds = if (bulletId in expandedNoteIds) {
                                                expandedNoteIds - bulletId
                                            } else {
                                                expandedNoteIds + bulletId
                                            }
                                        },
                                        onAttachment = {
                                            viewModel.requestAttachmentUpload(bulletId)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is BulletTreeUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Text(
                                text = "Couldn't load bullets",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.loadBullets(documentId) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        // Snackbar overlay
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Date picker dialog — triggered when user types "!!" in a bullet
    if (datePickerBulletId != null) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = {
                // Dismiss without selection — leave content as-is (keep the "!!")
                datePickerBulletId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val instant = java.time.Instant.ofEpochMilli(millis)
                        val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        val formatted = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        val bulletId = datePickerBulletId!!
                        // Read the CURRENT content (user may have typed more while picker was open)
                        val currentContent = contentOverrides[bulletId]
                            ?: (uiState as? BulletTreeUiState.Success)?.bullets
                                ?.find { it.id == bulletId }?.content ?: ""
                        val updated = currentContent.replaceFirst("!!", "!![$formatted]")
                        viewModel.updateContent(bulletId, updated)
                    }
                    datePickerBulletId = null
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    datePickerBulletId = null
                }) {
                    Text("Cancel")
                }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
}

/**
 * Shimmer placeholder shown while bullets are loading.
 *
 * Renders 5 rows at varying indent levels with animated alpha transition
 * to give a skeleton loading effect. Follows the same shimmer pattern
 * used in Phase 10 DocumentDrawerContent.
 */
@Composable
private fun ShimmerBulletRows() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    val indentLevels = listOf(0, 1, 2, 1, 0) // varying depths for visual interest

    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        indentLevels.forEach { indent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (indent * 28).dp,
                        top = 7.dp,
                        bottom = 7.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bullet circle placeholder
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                // Text line placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f + indent * 0.1f)
                        .height(17.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.5f),
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
        }
    }
}
