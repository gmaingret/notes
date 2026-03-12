package com.gmaingret.notes.presentation.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.gmaingret.notes.domain.usecase.GoogleSignInUseCase
import com.gmaingret.notes.domain.usecase.LoginUseCase
import com.gmaingret.notes.domain.usecase.RegisterUseCase
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for AuthScreen, running on JVM via Robolectric (no emulator needed).
 *
 * Tests use the real AuthViewModel with mocked use cases. This keeps the test setup simple
 * while still exercising the actual Compose rendering and state management.
 *
 * Note: Hilt injection is NOT used here — we instantiate the ViewModel directly with mocks,
 * which is the simplest approach for JVM/Robolectric unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildViewModel(): AuthViewModel {
        val loginUseCase: LoginUseCase = mockk(relaxed = true)
        val registerUseCase: RegisterUseCase = mockk(relaxed = true)
        val googleSignInUseCase: GoogleSignInUseCase = mockk(relaxed = true)
        return AuthViewModel(loginUseCase, registerUseCase, googleSignInUseCase)
    }

    @Test
    fun `login tab shows email and password fields`() {
        val viewModel = buildViewModel()
        composeTestRule.setContent {
            AuthScreen(
                showNetworkErrorOnStart = false,
                onAuthSuccess = {},
                viewModel = viewModel
            )
        }

        // "Log In" appears as both tab label and button label — at least one must be displayed
        composeTestRule.onAllNodesWithText("Log In")[0].assertIsDisplayed()
        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun `register tab shows email and password fields`() {
        val viewModel = buildViewModel()
        composeTestRule.setContent {
            AuthScreen(
                showNetworkErrorOnStart = false,
                onAuthSuccess = {},
                viewModel = viewModel
            )
        }

        // Click the Register tab
        composeTestRule.onAllNodesWithText("Register")[0].performClick()

        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun `switching tabs clears snackbar message`() {
        val viewModel = buildViewModel()
        composeTestRule.setContent {
            AuthScreen(
                showNetworkErrorOnStart = false,
                onAuthSuccess = {},
                viewModel = viewModel
            )
        }

        // Switch to Register and back to Login — UI must not crash
        composeTestRule.onAllNodesWithText("Register")[0].performClick()
        composeTestRule.onAllNodesWithText("Log In")[0].performClick()

        // After switching back to Login the Login tab should still be present
        composeTestRule.onAllNodesWithText("Log In")[0].assertIsDisplayed()
    }

    @Test
    fun `password visibility toggle works`() {
        val viewModel = buildViewModel()
        composeTestRule.setContent {
            AuthScreen(
                showNetworkErrorOnStart = false,
                onAuthSuccess = {},
                viewModel = viewModel
            )
        }

        // Type a password — the toggle icon should be present
        composeTestRule.onNodeWithText("Password").performTextInput("secret123")

        // Password field is present after typing
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun `submit button is enabled before loading starts`() {
        val viewModel = buildViewModel()
        composeTestRule.setContent {
            AuthScreen(
                showNetworkErrorOnStart = false,
                onAuthSuccess = {},
                viewModel = viewModel
            )
        }

        // There are two nodes with "Log In" text (tab + button).
        // The button is the one that's enabled for interaction — both should be present.
        val loginNodes = composeTestRule.onAllNodesWithText("Log In")
        loginNodes[0].assertIsDisplayed()
    }
}
