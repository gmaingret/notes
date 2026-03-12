package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.repository.DocumentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReorderDocumentUseCaseTest {

    private lateinit var documentRepository: DocumentRepository
    private lateinit var reorderDocumentUseCase: ReorderDocumentUseCase

    @Before
    fun setUp() {
        documentRepository = mockk()
        reorderDocumentUseCase = ReorderDocumentUseCase(documentRepository)
    }

    @Test
    fun `success calls reorderDocument with id and afterId`() = runTest {
        val document = Document(id = "2", title = "Moved", position = 1.5)
        coEvery { documentRepository.reorderDocument("2", "1") } returns Result.success(document)

        val result = reorderDocumentUseCase("2", "1")

        assertTrue(result.isSuccess)
        assertEquals(document, result.getOrNull())
        coVerify(exactly = 1) { documentRepository.reorderDocument("2", "1") }
    }

    @Test
    fun `success with null afterId moves document to first position`() = runTest {
        val document = Document(id = "2", title = "First Now", position = 0.5)
        coEvery { documentRepository.reorderDocument("2", null) } returns Result.success(document)

        val result = reorderDocumentUseCase("2", null)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { documentRepository.reorderDocument("2", null) }
    }

    @Test
    fun `failure returns Result failure`() = runTest {
        val exception = RuntimeException("Server error")
        coEvery { documentRepository.reorderDocument("2", "1") } returns Result.failure(exception)

        val result = reorderDocumentUseCase("2", "1")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
