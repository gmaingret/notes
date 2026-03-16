package com.gmaingret.notes.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmaingret.notes.MainActivity
import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.presentation.bookmarks.BookmarksScreen
import com.gmaingret.notes.presentation.bookmarks.BookmarksViewModel
import com.gmaingret.notes.presentation.bullet.BulletTreeScreen
import com.gmaingret.notes.presentation.bullet.BulletTreeViewModel
import com.gmaingret.notes.presentation.search.SearchResultItem
import com.gmaingret.notes.presentation.search.SearchUiState
import com.gmaingret.notes.presentation.search.SearchViewModel
import com.gmaingret.notes.presentation.tags.TagBrowserScreen
import com.gmaingret.notes.presentation.tags.TagsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val isDrawerRefreshing by viewModel.isRefreshing.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteConfirmation by remember { mutableStateOf<Document?>(null) }

    // Search state
    val searchViewModel: SearchViewModel = hiltViewModel()
    val searchUiState by searchViewModel.uiState.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    // Bookmarks ViewModel
    val bookmarksViewModel: BookmarksViewModel = hiltViewModel()
    val bookmarksUiState by bookmarksViewModel.uiState.collectAsState()

    // Refresh bookmarks whenever the bookmarks view becomes visible
    val showBookmarks = (uiState as? MainUiState.Success)?.showBookmarks == true
    LaunchedEffect(showBookmarks) {
        if (showBookmarks) {
            bookmarksViewModel.loadBookmarks()
        }
    }

    // Tags ViewModel
    val tagsViewModel: TagsViewModel = hiltViewModel()
    val tagsUiState by tagsViewModel.uiState.collectAsState()

    // BulletTreeViewModel — shared singleton (Activity-scoped via hiltViewModel).
    // Used to call setScrollTarget() directly when a search result or bookmark is tapped,
    // bypassing the Crossfade parameter path which was found unreliable across 3 user reports.
    val bulletTreeViewModel: BulletTreeViewModel = hiltViewModel()

    // Refresh tags whenever the tags view becomes visible
    val showTags = (uiState as? MainUiState.Success)?.showTags == true
    LaunchedEffect(showTags) {
        if (showTags) {
            tagsViewModel.loadTags()
        }
    }

    // Collect snackbar messages from ViewModel
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Consume a pending widget deep-link document ID and navigate to the document.
    // Keyed on Unit so it runs once when MainScreen enters the composition.
    // consumeWidgetDocumentId() returns-and-clears the pending ID atomically.
    val activity = LocalContext.current as? MainActivity
    LaunchedEffect(Unit) {
        activity?.consumeWidgetDocumentId()?.let { docId ->
            viewModel.openDocument(docId)
        }
    }

    // Refresh documents when drawer opens
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            viewModel.refreshDocuments()
        }
    }

    // Focus search field when search bar activates
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    // BackHandler: close search if active
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
        searchViewModel.reset()
    }

    // Compute TopAppBar title from current state
    val appBarTitle = when (val state = uiState) {
        is MainUiState.Success -> {
            when {
                state.showBookmarks -> "Bookmarks"
                state.showTags -> "Tags"
                state.openDocumentId != null ->
                    state.documents.find { it.id == state.openDocumentId }?.title ?: "Notes"
                else -> "Notes"
            }
        }
        else -> "Notes"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DocumentDrawerContent(
                    uiState = uiState,
                    onDocumentClick = { doc ->
                        viewModel.openDocument(doc.id)
                        scope.launch { drawerState.close() }
                    },
                    onCreateDocument = {
                        viewModel.createDocument()
                    },
                    onRename = { docId ->
                        viewModel.startRename(docId)
                    },
                    onDelete = { docId ->
                        val state = uiState as? MainUiState.Success
                        val doc = state?.documents?.find { it.id == docId }
                        if (doc != null) {
                            scope.launch { drawerState.close() }
                            deleteConfirmation = doc
                        }
                    },
                    onSubmitRename = { docId, title ->
                        viewModel.submitRename(docId, title)
                    },
                    onCancelRename = {
                        viewModel.cancelRename()
                    },
                    onMoveLocally = { fromIndex, toIndex ->
                        viewModel.moveDocumentLocally(fromIndex, toIndex)
                    },
                    onCommitReorder = { docId ->
                        viewModel.commitReorder(docId)
                    },
                    onRetry = {
                        viewModel.refreshDocuments()
                    },
                    onBookmarksClick = {
                        viewModel.showBookmarks()
                        scope.launch { drawerState.close() }
                    },
                    onTagsClick = {
                        viewModel.showTags()
                        scope.launch { drawerState.close() }
                    },
                    onLogout = {
                        viewModel.logout(onComplete = onLogout)
                    },
                    isRefreshing = isDrawerRefreshing,
                    onRefresh = { viewModel.refreshDocuments() }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            // Inline search text field
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { newQuery ->
                                    searchQuery = newQuery
                                    searchViewModel.onQueryChange(newQuery)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search bullets...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        } else {
                            Text(appBarTitle)
                        }
                    },
                    navigationIcon = {
                        if (isSearchActive) {
                            // Back arrow to close search
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                                searchViewModel.reset()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close search"
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open drawer"
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSearchActive) {
                            // Clear button when search has text
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    searchViewModel.onQueryChange("")
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is MainUiState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is MainUiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.loadDocuments() },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    is MainUiState.Empty -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No documents",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.createDocument() },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Create document")
                            }
                        }
                    }
                    is MainUiState.Success -> {
                        // Crossfade between bookmarks, tags, and bullet tree for smooth screen transitions
                        val contentKey = when {
                            state.showBookmarks -> "bookmarks"
                            state.showTags -> "tags"
                            state.openDocumentId != null -> "doc:${state.openDocumentId}"
                            else -> "empty"
                        }
                        Crossfade(targetState = contentKey, label = "content-crossfade") { key ->
                            when {
                                key == "bookmarks" -> {
                                    BookmarksScreen(
                                        uiState = bookmarksUiState,
                                        onBookmarkClick = { bookmark ->
                                            viewModel.navigateToBullet(bookmark.documentId, bookmark.bulletId)
                                            // Set scroll target directly on BulletTreeViewModel
                                            bulletTreeViewModel.setScrollTarget(bookmark.bulletId)
                                        },
                                        onRetry = { bookmarksViewModel.loadBookmarks() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                key == "tags" -> {
                                    TagBrowserScreen(
                                        uiState = tagsUiState,
                                        onTagClick = { chipType, value ->
                                            // Build search query matching the chip format
                                            val query = when (chipType) {
                                                "tag" -> "#$value"
                                                "mention" -> "@$value"
                                                "date" -> "!![${value}]"
                                                else -> value
                                            }
                                            isSearchActive = true
                                            searchQuery = query
                                            searchViewModel.onQueryChange(query)
                                        },
                                        onRetry = { tagsViewModel.loadTags() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                key.startsWith("doc:") -> {
                                    val docId = state.openDocumentId ?: return@Crossfade
                                    BulletTreeScreen(
                                        documentId = docId,
                                        documentTitle = state.documents.find { it.id == docId }?.title ?: "Notes",
                                        pendingScrollToBulletId = state.pendingScrollToBulletId,
                                        onClearPendingScroll = { viewModel.clearPendingScroll() },
                                        onChipClick = { chipText ->
                                            isSearchActive = true
                                            searchQuery = chipText
                                            searchViewModel.onQueryChange(chipText)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {
                                    Text("Select a document")
                                }
                            }
                        }
                    }
                }

                // Search results overlay — rendered on top of content when search is active
                if (isSearchActive) {
                    Surface(
                        modifier = Modifier.fillMaxSize().align(Alignment.TopCenter),
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                    ) {
                        when (val searchState = searchUiState) {
                            is SearchUiState.Idle -> {
                                // Show nothing — waiting for user to type
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Type to search bullets",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is SearchUiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            is SearchUiState.Empty -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No results",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is SearchUiState.Error -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = searchState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            is SearchUiState.Success -> {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    searchState.results.forEach { (docTitle, results) ->
                                        stickyHeader {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = docTitle,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    modifier = Modifier.padding(
                                                        horizontal = 16.dp,
                                                        vertical = 8.dp
                                                    )
                                                )
                                            }
                                        }
                                        items(results) { result ->
                                            SearchResultItem(
                                                result = result,
                                                query = searchQuery,
                                                onClick = {
                                                    viewModel.navigateToBullet(
                                                        result.documentId,
                                                        result.bulletId
                                                    )
                                                    bulletTreeViewModel.setScrollTarget(result.bulletId)
                                                    isSearchActive = false
                                                    searchQuery = ""
                                                    searchViewModel.reset()
                                                }
                                            )
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog rendered at MainScreen level (outside drawer)
    deleteConfirmation?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteConfirmation = null },
            title = { Text("Delete ${doc.title}?") },
            text = { Text("This will delete all bullets in this document.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(doc.id)
                        deleteConfirmation = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
