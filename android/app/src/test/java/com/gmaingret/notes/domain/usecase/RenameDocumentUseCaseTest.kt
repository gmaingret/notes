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

class RenameDocumentUseCaseTest {

    private lateinit var documentRepository: DocumentRepository
    private lateinit var renameDocumentUseCase: RenameDocumentUseCase

    @Before
    fun setUp() {
        documentRepository = mockk()
        renameDocumentUseCase = RenameDocumentUseCase(documentRepository)
    }

    @Test
    fun `success calls renameDocument and returns updated document`() = runTest {
        val document = Document(id = "1", title = "New Title", position = 1.0)
        coEvery { documentRepository.renameDocument("1", "New Title") } returns Result.success(document)

        val result = renameDocumentUseCase("1", "New Title")

        assertTrue(result.isSuccess)
        assertEquals(document, result.getOrNull())
        coVerify(exactly = 1) { documentRepository.renameDocument("1", "New Title") }
    }

    @Test
    fun `failure returns Result failure`() = runTest {
        val exception = RuntimeException("Not found")
        coEvery { documentRepository.renameDocument("1", "New Title") } returns Result.failure(exception)

        val result = renameDocumentUseCase("1", "New Title")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
