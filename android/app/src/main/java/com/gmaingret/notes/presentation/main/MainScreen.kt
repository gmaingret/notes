package com.gmaingret.notes.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmaingret.notes.domain.model.Document
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf<Document?>(null) }

    // Collect snackbar messages from ViewModel
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Refresh documents when drawer opens
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            viewModel.refreshDocuments()
        }
    }

    // Compute TopAppBar title from current state
    val appBarTitle = when (val state = uiState) {
        is MainUiState.Success -> {
            if (state.openDocumentId != null) {
                state.documents.find { it.id == state.openDocumentId }?.title ?: "Notes"
            } else {
                "Notes"
            }
        }
        else -> "Notes"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
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
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(appBarTitle) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open drawer"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Log out") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.logout(onComplete = onLogout)
                                }
                            )
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
                        if (state.openDocumentId != null) {
                            val openDoc = state.documents.find { it.id == state.openDocumentId }
                            if (openDoc != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = openDoc.title,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Text(
                                        text = "Content area — Phase 11 will add the bullet tree editor here",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        } else {
                            Text("Select a document")
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
