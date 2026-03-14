package com.gmaingret.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmaingret.notes.domain.model.AuthState
import com.gmaingret.notes.presentation.navigation.AuthRoute
import com.gmaingret.notes.presentation.navigation.MainRoute
import com.gmaingret.notes.presentation.navigation.NotesApp
import com.gmaingret.notes.presentation.splash.SplashViewModel
import com.gmaingret.notes.presentation.theme.NotesTheme
import com.gmaingret.notes.widget.OPEN_DOCUMENT_ID
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

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
            NotesTheme {
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
