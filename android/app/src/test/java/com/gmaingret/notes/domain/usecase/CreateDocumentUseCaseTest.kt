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

class CreateDocumentUseCaseTest {

    private lateinit var documentRepository: DocumentRepository
    private lateinit var createDocumentUseCase: CreateDocumentUseCase

    @Before
    fun setUp() {
        documentRepository = mockk()
        createDocumentUseCase = CreateDocumentUseCase(documentRepository)
    }

    @Test
    fun `success calls createDocument and returns document`() = runTest {
        val document = Document(id = "1", title = "Untitled", position = 1.0)
        coEvery { documentRepository.createDocument("Untitled") } returns Result.success(document)

        val result = createDocumentUseCase("Untitled")

        assertTrue(result.isSuccess)
        assertEquals(document, result.getOrNull())
        coVerify(exactly = 1) { documentRepository.createDocument("Untitled") }
    }

    @Test
    fun `failure returns Result failure`() = runTest {
        val exception = RuntimeException("Server error")
        coEvery { documentRepository.createDocument("Untitled") } returns Result.failure(exception)

        val result = createDocumentUseCase("Untitled")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
