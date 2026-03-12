package com.gmaingret.notes.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.gmaingret.notes.presentation.auth.AuthScreen
import com.gmaingret.notes.presentation.main.MainScreen

/**
 * Top-level composable that owns the Navigation3 back stack and renders the
 * correct screen based on the current back stack head.
 *
 * [initialRoute] is determined by MainActivity based on the splash screen auth check:
 * - MainRoute when a valid refresh token was confirmed
 * - AuthRoute() for normal unauthenticated start
 * - AuthRoute(showNetworkError = true) when cold-start token refresh failed due to network error
 */
@Composable
fun NotesApp(initialRoute: NavKey) {
    val backStack = rememberNavBackStack(initialRoute)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<AuthRoute> { route ->
                AuthScreen(
                    showNetworkErrorOnStart = route.showNetworkError,
                    onAuthSuccess = {
                        backStack.clear()
                        backStack.add(MainRoute)
                    }
                )
            }
            entry<MainRoute> {
                MainScreen(
                    onLogout = {
                        backStack.clear()
                        backStack.add(AuthRoute())
                    }
                )
            }
        }
    )
}
