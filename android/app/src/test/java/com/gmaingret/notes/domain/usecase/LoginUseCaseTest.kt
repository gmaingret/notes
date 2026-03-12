package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.User
import com.gmaingret.notes.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var loginUseCase: LoginUseCase

    @Before
    fun setUp() {
        authRepository = mockk()
        loginUseCase = LoginUseCase(authRepository)
    }

    @Test
    fun `login success returns user`() = runTest {
        val expectedUser = User(id = "1", email = "test@example.com")
        coEvery { authRepository.login("test@example.com", "password123") } returns Result.success(expectedUser)

        val result = loginUseCase("test@example.com", "password123")

        assertTrue(result.isSuccess)
        assertEquals(expectedUser, result.getOrNull())
        coVerify(exactly = 1) { authRepository.login("test@example.com", "password123") }
    }

    @Test
    fun `login failure returns error`() = runTest {
        val exception = RuntimeException("Invalid credentials")
        coEvery { authRepository.login("test@example.com", "wrongpass") } returns Result.failure(exception)

        val result = loginUseCase("test@example.com", "wrongpass")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 1) { authRepository.login("test@example.com", "wrongpass") }
    }
}
