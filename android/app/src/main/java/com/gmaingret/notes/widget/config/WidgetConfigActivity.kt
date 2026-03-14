package com.gmaingret.notes.widget.config

import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.usecase.GoogleSignInUseCase
import com.gmaingret.notes.presentation.theme.NotesTheme
import com.gmaingret.notes.widget.NotesWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen config activity for the Notes home screen widget.
 *
 * Opens automatically when the user places the widget on their home screen.
 * Also opens on long-press reconfigure (enabled via android:configure in appwidget-provider XML).
 *
 * Critical pattern: RESULT_CANCELED is set BEFORE any UI so that pressing Back
 * discards the widget placement. Only a confirmed document tap switches to RESULT_OK.
 *
 * If the user is not authenticated, an inline login form appears before the document list.
 * Google SSO is supported via Credential Manager (same pattern as the main AuthScreen).
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private val viewModel: WidgetConfigViewModel by viewModels()

    /**
     * Injected so we can trigger Credential Manager from the Activity context.
     * GoogleSignInUseCase requires an Activity context to show the bottom-sheet picker.
     */
    @Inject
    lateinit var googleSignInUseCase: GoogleSignInUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract widget ID from launcher intent
        val appWidgetId = intent?.extras?.getInt(
            EXTRA_APPWIDGET_ID,
            INVALID_APPWIDGET_ID
        ) ?: INVALID_APPWIDGET_ID

        // Discard immediately if no valid widget ID
        if (appWidgetId == INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // CRITICAL: set RESULT_CANCELED immediately so Back press discards placement
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_APPWIDGET_ID, appWidgetId))

        enableEdgeToEdge()

        setContent {
            NotesTheme {
                // Collect one-shot DocumentSelected event to finalize RESULT_OK
                LaunchedEffect(Unit) {
                    viewModel.documentSelectedEvent.collect { docId ->
                        // Write docId into Glance widget preferences — this triggers
                        // provideContent recomposition via currentState<Preferences>()
                        NotesWidget.setDocumentId(this@WidgetConfigActivity, appWidgetId, docId)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                        finish()
                    }
                }

                val uiState by viewModel.uiState.collectAsState()

                WidgetConfigScreen(
                    uiState = uiState,
                    onLogin = { email, password -> viewModel.login(email, password) },
                    onGoogleSignIn = {
                        // Google SSO: launch from lifecycleScope so we can call the
                        // suspend use case with Activity context for Credential Manager
                        lifecycleScope.launch {
                            val result = googleSignInUseCase(this@WidgetConfigActivity)
                            result.onSuccess {
                                // Auth successful — ViewModel re-checks and loads documents
                                viewModel.reloadDocuments()
                            }
                            result.onFailure { e ->
                                viewModel.setError(e.message ?: "Google sign-in failed")
                            }
                        }
                    },
                    onDocumentSelected = { docId ->
                        viewModel.selectDocument(appWidgetId, docId)
                    },
                    onRetry = { viewModel.retry() }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen composable (package-private for testability)
// ---------------------------------------------------------------------------

@Composable
internal fun WidgetConfigScreen(
    uiState: ConfigUiState,
    onLogin: (email: String, password: String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onDocumentSelected: (docId: String) -> Unit,
    onRetry: () -> Unit
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is ConfigUiState.Loading -> {
                    CircularProgressIndicator()
                }

                is ConfigUiState.NeedsLogin -> {
                    LoginForm(
                        onLogin = onLogin,
                        onGoogleSignIn = onGoogleSignIn
                    )
                }

                is ConfigUiState.DocumentsLoaded -> {
                    DocumentPickerList(
                        documents = uiState.documents,
                        onDocumentSelected = onDocumentSelected
                    )
                }

                is ConfigUiState.Error -> {
                    ErrorView(
                        message = uiState.message,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Login form
// ---------------------------------------------------------------------------

@Composable
private fun LoginForm(
    onLogin: (email: String, password: String) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isGoogleAvailable = remember { GoogleSignInUseCase.isGoogleSignInAvailable(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Sign in to Notes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Select a document to display on your widget",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
                        },
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Login button
        Button(
            onClick = { onLogin(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank() && password.isNotBlank()
        ) {
            Text("Log In")
        }

        // Google SSO divider + button — only shown when Credential Manager is available
        if (isGoogleAvailable) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "  or  ",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onGoogleSignIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with Google")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// Document picker list
// ---------------------------------------------------------------------------

@Composable
private fun DocumentPickerList(
    documents: List<Document>,
    onDocumentSelected: (docId: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Choose a document",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(documents, key = { it.id }) { document ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDocumentSelected(document.id) }
                )
                HorizontalDivider()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Error view
// ---------------------------------------------------------------------------

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}
