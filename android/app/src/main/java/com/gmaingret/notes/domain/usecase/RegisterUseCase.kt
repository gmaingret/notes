package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.User
import com.gmaingret.notes.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> =
        authRepository.register(email, password)
}
