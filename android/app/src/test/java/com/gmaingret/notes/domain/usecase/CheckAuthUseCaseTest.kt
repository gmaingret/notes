package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class CheckAuthUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var checkAuthUseCase: CheckAuthUseCase

    @Before
    fun setUp() {
        authRepository = mockk()
        checkAuthUseCase = CheckAuthUseCase(authRepository)
    }

    @Test
    fun `returns true when refresh succeeds`() = runTest {
        coEvery { authRepository.refresh() } returns Result.success("new_access_token")

        val result = checkAuthUseCase()

        assertTrue(result)
        coVerify(exactly = 1) { authRepository.refresh() }
    }

    @Test
    fun `returns false when refresh fails`() = runTest {
        coEvery { authRepository.refresh() } returns Result.failure(RuntimeException("Unauthorized"))

        val result = checkAuthUseCase()

        assertFalse(result)
        coVerify(exactly = 1) { authRepository.refresh() }
    }

    @Test
    fun `returns false on exception`() = runTest {
        coEvery { authRepository.refresh() } throws IOException("Network unavailable")

        val result = runCatching { checkAuthUseCase() }.getOrElse { false }

        assertFalse(result)
    }
}
