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

class GetDocumentsUseCaseTest {

    private lateinit var documentRepository: DocumentRepository
    private lateinit var getDocumentsUseCase: GetDocumentsUseCase

    @Before
    fun setUp() {
        documentRepository = mockk()
        getDocumentsUseCase = GetDocumentsUseCase(documentRepository)
    }

    @Test
    fun `success returns sorted list of documents`() = runTest {
        val documents = listOf(
            Document(id = "1", title = "First", position = 1.0),
            Document(id = "2", title = "Second", position = 2.0)
        )
        coEvery { documentRepository.getDocuments() } returns Result.success(documents)

        val result = getDocumentsUseCase()

        assertTrue(result.isSuccess)
        assertEquals(documents, result.getOrNull())
        coVerify(exactly = 1) { documentRepository.getDocuments() }
    }

    @Test
    fun `failure returns Result failure`() = runTest {
        val exception = RuntimeException("Network error")
        coEvery { documentRepository.getDocuments() } returns Result.failure(exception)

        val result = getDocumentsUseCase()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 1) { documentRepository.getDocuments() }
    }
}
