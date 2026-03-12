package com.gmaingret.notes

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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash screen until the auth check resolves (never leave on CHECKING)
        splashScreen.setKeepOnScreenCondition {
            splashViewModel.authState.value == AuthState.CHECKING
        }

        enableEdgeToEdge()

        setContent {
            NotesTheme {
                val authState by splashViewModel.authState.collectAsStateWithLifecycle()
                val networkError by splashViewModel.coldStartNetworkError.collectAsStateWithLifecycle()

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
