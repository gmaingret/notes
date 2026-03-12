package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Validates the user's session on cold start by calling the refresh endpoint.
 *
 * A successful refresh proves both that the refreshToken cookie is still valid
 * (not expired, not revoked) and that the backend is reachable. The new access
 * token is automatically saved by AuthRepositoryImpl.
 *
 * Used by the splash / SplashViewModel to determine the initial navigation destination.
 */
class CheckAuthUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Boolean =
        authRepository.refresh().isSuccess
}
