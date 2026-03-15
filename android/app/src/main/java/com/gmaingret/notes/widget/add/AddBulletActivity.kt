package com.gmaingret.notes.widget.add

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.usecase.CreateBulletUseCase
import com.gmaingret.notes.presentation.theme.NotesTheme
import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.NotesWidget
import com.gmaingret.notes.widget.WidgetBullet
import com.gmaingret.notes.widget.WidgetStateStore
import com.gmaingret.notes.widget.stripMarkdownSyntax
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Overlay Activity for adding bullets directly from the home screen widget.
 *
 * Launched by the [+] button in the widget header. Shows a transparent
 * floating dialog over the home screen with a pre-focused text field.
 *
 * On Enter: creates a bullet optimistically and closes on success.
 * On Cancel / tap-outside: dismisses without creating anything.
 * On API failure: stays open with text preserved and shows a toast.
 * On auth error: shows "Session expired" toast and closes.
 */
@AndroidEntryPoint
class AddBulletActivity : ComponentActivity() {

    @Inject
    lateinit var createBulletUseCase: CreateBulletUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val docId = intent.getStringExtra("doc_id")
        if (docId == null) {
            finish()
            return
        }

        // Dismiss dialog when user taps outside (touches the dimmed background)
        setFinishOnTouchOutside(true)

        setContent {
            NotesTheme {
                AddBulletDialog(
                    onConfirm = { text ->
                        lifecycleScope.launch {
                            val store = WidgetStateStore.getInstance(applicationContext)

                            val result = performAddBullet(
                                docId = docId,
                                content = text,
                                store = store,
                                context = applicationContext,
                                createBulletUseCase = createBulletUseCase
                            )

                            when (result) {
                                is AddBulletResult.Success -> finish()
                                is AddBulletResult.Failure -> {
                                    Toast.makeText(
                                        applicationContext,
                                        result.message,
                                        LENGTH_SHORT
                                    ).show()
                                    // Dialog stays open — do NOT finish()
                                }
                                is AddBulletResult.AuthError -> {
                                    Toast.makeText(
                                        applicationContext,
                                        "Session expired",
                                        LENGTH_SHORT
                                    ).show()
                                    finish()
                                }
                            }
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialog composable
// ---------------------------------------------------------------------------

/**
 * Floating dialog composable shown inside AddBulletActivity.
 *
 * Auto-focuses the text field and shows the soft keyboard on first composition.
 * Pressing Enter/Done with non-blank text calls [onConfirm] with the trimmed text.
 * Cancel button calls [onDismiss].
 */
@Composable
internal fun AddBulletDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("New bullet") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = text.trim()
                        if (trimmed.isNotBlank()) {
                            onConfirm(trimmed)
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotBlank()) {
                            onConfirm(trimmed)
                        }
                    },
                    enabled = text.isNotBlank()
                ) {
                    Text("OK")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Result sealed class
// ---------------------------------------------------------------------------

/**
 * Result of a [performAddBullet] call.
 */
sealed class AddBulletResult {
    data object Success : AddBulletResult()
    data class Failure(val message: String) : AddBulletResult()
    data object AuthError : AddBulletResult()
}

// ---------------------------------------------------------------------------
// Core business logic (extracted for unit testability)
// ---------------------------------------------------------------------------

/**
 * Core add logic extracted for unit testability.
 *
 * Performs optimistic insert at the top of the list and pushes to Glance
 * immediately so the widget updates instantly. Then calls the API:
 * - On success: replaces the temp bullet with the server-assigned ID.
 * - On failure: rolls back to the original list.
 * - On auth error: additionally sets SESSION_EXPIRED display state.
 *
 * @param docId The document to add the bullet to.
 * @param content The raw user-typed content (may contain markdown syntax).
 * @param store The WidgetStateStore to read from and write to.
 * @param context Application context for pushing state to Glance.
 * @param createBulletUseCase Use case that calls the server create API.
 */
internal suspend fun performAddBullet(
    docId: String,
    content: String,
    store: WidgetStateStore,
    context: Context,
    createBulletUseCase: CreateBulletUseCase
): AddBulletResult {
    val original = store.getBullets()
    val tempId = "temp-${System.nanoTime()}"
    val tempBullet = WidgetBullet(
        id = tempId,
        content = stripMarkdownSyntax(content),
        isComplete = false
    )

    // Optimistic insert at position 0 (top of list)
    val optimisticList = listOf(tempBullet) + original

    // Fast path: push directly to Glance for instant UI update
    NotesWidget.pushToGlanceDirect(context, DisplayState.CONTENT, optimisticList)

    // Persist to durable store (doesn't block UI)
    store.saveBullets(optimisticList)
    store.saveDisplayState(DisplayState.CONTENT)

    // API call
    val result = createBulletUseCase(
        CreateBulletRequest(
            documentId = docId,
            parentId = null,
            afterId = null,
            content = content
        )
    )

    return if (result.isSuccess) {
        val bullet: Bullet = result.getOrThrow()
        val realBullet = WidgetBullet(
            id = bullet.id,
            content = stripMarkdownSyntax(bullet.content),
            isComplete = bullet.isComplete
        )
        // Replace temp bullet with real server bullet at the top
        store.saveBullets(listOf(realBullet) + original)
        store.saveDisplayState(DisplayState.CONTENT)
        NotesWidget.pushStateToGlance(context)
        AddBulletResult.Success
    } else {
        val ex = result.exceptionOrNull()
        if (isAuthError(ex)) {
            store.saveBullets(original)
            store.saveDisplayState(DisplayState.SESSION_EXPIRED)
            NotesWidget.pushStateToGlance(context)
            AddBulletResult.AuthError
        } else {
            store.saveBullets(original)
            store.saveDisplayState(
                if (original.isEmpty()) DisplayState.EMPTY else DisplayState.CONTENT
            )
            NotesWidget.pushStateToGlance(context)
            AddBulletResult.Failure("Couldn't add bullet")
        }
    }
}

/**
 * Returns true if the exception indicates an authentication failure (HTTP 401 or
 * messages containing "401", "unauthorized", or "unauthenticated").
 */
private fun isAuthError(e: Throwable?): Boolean {
    if (e == null) return false
    if (e is HttpException && e.code() == 401) return true
    val msg = e.message?.lowercase() ?: ""
    return msg.contains("401") || msg.contains("unauthorized") || msg.contains("unauthenticated")
}
