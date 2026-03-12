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
import retrofit2.HttpException

class RegisterUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var registerUseCase: RegisterUseCase

    @Before
    fun setUp() {
        authRepository = mockk()
        registerUseCase = RegisterUseCase(authRepository)
    }

    @Test
    fun `register success returns user`() = runTest {
        val expectedUser = User(id = "2", email = "newuser@example.com")
        coEvery { authRepository.register("newuser@example.com", "securepass1") } returns Result.success(expectedUser)

        val result = registerUseCase("newuser@example.com", "securepass1")

        assertTrue(result.isSuccess)
        assertEquals(expectedUser, result.getOrNull())
        coVerify(exactly = 1) { authRepository.register("newuser@example.com", "securepass1") }
    }

    @Test
    fun `register with existing email returns failure`() = runTest {
        val exception = RuntimeException("Email already registered")
        coEvery { authRepository.register("existing@example.com", "password1") } returns Result.failure(exception)

        val result = registerUseCase("existing@example.com", "password1")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 1) { authRepository.register("existing@example.com", "password1") }
    }
}
