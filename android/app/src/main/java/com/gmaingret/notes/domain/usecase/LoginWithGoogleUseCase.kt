package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.User
import com.gmaingret.notes.domain.repository.AuthRepository
import javax.inject.Inject

class LoginWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> =
        authRepository.loginWithGoogle(idToken)
}
