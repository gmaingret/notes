package com.gmaingret.notes.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Authentication screen route.
 *
 * [showNetworkError] is true when the cold-start token refresh failed due to a
 * network error. AuthScreen reads this flag via its showNetworkErrorOnStart
 * parameter and immediately shows a Snackbar via LaunchedEffect.
 */
@Serializable
data class AuthRoute(val showNetworkError: Boolean = false) : NavKey

/**
 * Main (post-auth) screen route.
 */
@Serializable
object MainRoute : NavKey
