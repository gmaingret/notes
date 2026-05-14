package com.gmaingret.notes.domain.usecase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.gmaingret.notes.BuildConfig
import com.gmaingret.notes.domain.model.User
import javax.inject.Inject

/**
 * Handles the Google Sign-In two-step Credential Manager flow:
 *
 * Step 1: Silent/bottom-sheet picker using GetGoogleIdOption (filterByAuthorizedAccounts=true).
 * Step 2: On NoCredentialException, fall back to full GetSignInWithGoogleOption picker.
 *
 * On success, extracts the Google ID token and delegates to [LoginWithGoogleUseCase] to
 * exchange it with the backend.
 *
 * Uses the Web OAuth client ID per Credential Manager requirements — only Web/
 * iOS/Desktop OAuth clients can be ID token audiences. The Android OAuth client
 * still exists in Google Cloud Console with this app's package + SHA-1; that
 * registration is what makes Play Services trust the call. The server's
 * /api/auth/google/token endpoint also accepts tokens for GOOGLE_ANDROID_CLIENT_ID
 * (left in place for future flows but unused in practice).
 */
class GoogleSignInUseCase @Inject constructor(
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase
) {

    suspend operator fun invoke(context: Context): Result<User> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

            // Step 1: Try silent/bottom-sheet sign-in (only authorized accounts)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val idToken = try {
                val result = credentialManager.getCredential(context, request)
                extractIdToken(result.credential)
            } catch (_: NoCredentialException) {
                // Step 2: Fall back to full Google Sign-In picker
                val signInOption = GetSignInWithGoogleOption.Builder(clientId).build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val fallbackResult = credentialManager.getCredential(context, fallbackRequest)
                extractIdToken(fallbackResult.credential)
            }

            if (idToken != null) {
                loginWithGoogleUseCase(idToken)
            } else {
                Result.failure(IllegalStateException("Failed to extract Google ID token from credential"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractIdToken(credential: androidx.credentials.Credential): String? {
        return if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    }

    companion object {
        /**
         * Checks if Google Sign-In via Credential Manager is available on this device.
         *
         * Returns true on all supported devices (Android 9+). Returns false if
         * CredentialManager throws UnsupportedOperationException (e.g., some emulators).
         * Per locked decision: hide Google button entirely if unavailable (no crash, no error).
         */
        fun isGoogleSignInAvailable(context: Context): Boolean {
            return try {
                CredentialManager.create(context)
                true
            } catch (_: UnsupportedOperationException) {
                false
            }
        }
    }
}
