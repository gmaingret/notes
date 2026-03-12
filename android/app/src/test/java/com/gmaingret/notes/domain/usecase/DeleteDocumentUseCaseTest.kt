package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.DocumentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeleteDocumentUseCaseTest {

    private lateinit var documentRepository: DocumentRepository
    private lateinit var deleteDocumentUseCase: DeleteDocumentUseCase

    @Before
    fun setUp() {
        documentRepository = mockk()
        deleteDocumentUseCase = DeleteDocumentUseCase(documentRepository)
    }

    @Test
    fun `success calls deleteDocument and returns Unit`() = runTest {
        coEvery { documentRepository.deleteDocument("1") } returns Result.success(Unit)

        val result = deleteDocumentUseCase("1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { documentRepository.deleteDocument("1") }
    }

    @Test
    fun `failure returns Result failure`() = runTest {
        val exception = RuntimeException("Not found")
        coEvery { documentRepository.deleteDocument("1") } returns Result.failure(exception)

        val result = deleteDocumentUseCase("1")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
