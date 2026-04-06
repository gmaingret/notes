package com.gmaingret.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gmaingret.notes.domain.model.AuthState
import com.gmaingret.notes.presentation.navigation.AuthRoute
import com.gmaingret.notes.presentation.navigation.MainRoute
import com.gmaingret.notes.presentation.navigation.NotesApp
import com.gmaingret.notes.presentation.splash.SplashViewModel
import com.gmaingret.notes.presentation.theme.NotesTheme
import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.NotesWidget
import com.gmaingret.notes.widget.OPEN_DOCUMENT_ID
import com.gmaingret.notes.widget.WidgetDebugLog
import com.gmaingret.notes.widget.WidgetEntryPoint
import com.gmaingret.notes.widget.WidgetStateStore
import com.gmaingret.notes.widget.WidgetUiState
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

    @Inject lateinit var tokenStore: com.gmaingret.notes.data.local.TokenStore

    /**
     * Stores the document ID requested via a widget tap.
     * Set in onCreate/onNewIntent; consumed and cleared by MainScreen via
     * [consumeWidgetDocumentId].
     */
    var pendingWidgetDocId: String? = null
        private set

    /**
     * Returns the pending widget document ID and clears it in one atomic step,
     * ensuring the navigation happens exactly once even if the composable recomposes.
     */
    fun consumeWidgetDocumentId(): String? {
        val id = pendingWidgetDocId
        pendingWidgetDocId = null
        return id
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash screen until the auth check resolves (never leave on CHECKING)
        splashScreen.setKeepOnScreenCondition {
            splashViewModel.authState.value == AuthState.CHECKING
        }

        enableEdgeToEdge()
        handleWidgetIntent(intent)

        setContent {
            val themeMode by tokenStore.themeModeFlow().collectAsStateWithLifecycle(initialValue = "system")
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            NotesTheme(darkTheme = isDark) {
                val authState by splashViewModel.authState.collectAsStateWithLifecycle()
                val networkError by splashViewModel.coldStartNetworkError.collectAsStateWithLifecycle()

                // Only render NotesApp after auth check resolves.
                // rememberNavBackStack only uses initialRoute on first creation,
                // so we must not compose it while authState is still CHECKING.
                if (authState != AuthState.CHECKING) {
                    val initialRoute = when {
                        authState == AuthState.AUTHENTICATED -> MainRoute
                        networkError -> AuthRoute(showNetworkError = true)
                        else -> AuthRoute()
                    }

                    NotesApp(initialRoute = initialRoute)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val store = WidgetStateStore.getInstance(this@MainActivity)
                val currentState = store.getDisplayState()

                WidgetDebugLog.log(applicationContext, "WidgetMainActivity", "onResume: currentState=$currentState")

                if (currentState != DisplayState.SESSION_EXPIRED) return@launch

                val manager = androidx.glance.appwidget.GlanceAppWidgetManager(this@MainActivity)
                val glanceIds = manager.getGlanceIds(NotesWidget::class.java)
                if (glanceIds.isEmpty()) return@launch

                val docId = store.getFirstDocumentId() ?: return@launch

                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    applicationContext, WidgetEntryPoint::class.java
                )
                val widget = NotesWidget()
                when (val result = widget.fetchWidgetData(entryPoint, docId)) {
                    is WidgetUiState.Content -> {
                        store.saveBullets(result.bullets)
                        store.saveDisplayState(DisplayState.CONTENT)
                        store.saveDocumentTitle(result.documentTitle)
                    }
                    is WidgetUiState.Empty -> {
                        store.saveBullets(emptyList())
                        store.saveDisplayState(DisplayState.EMPTY)
                    }
                    else -> { /* Don't overwrite with errors on resume */ }
                }
                NotesWidget.pushStateToGlance(this@MainActivity)
            } catch (_: Exception) {
                // Safe to ignore — widget may not exist
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val docId = intent?.getStringExtra(OPEN_DOCUMENT_ID)
        if (!docId.isNullOrEmpty()) {
            pendingWidgetDocId = docId
        }
    }
}
